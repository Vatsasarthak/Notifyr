package com.yash.Notifyr;

import com.yash.Notifyr.dto.CampaignRequest;
import com.yash.Notifyr.dto.NotificationMessage;
import com.yash.Notifyr.dto.NotificationRequest;
import com.yash.Notifyr.dto.RecipientRequest;
import com.yash.Notifyr.dto.TemplateRequest;
import com.yash.Notifyr.entity.CampaignStatus;
import com.yash.Notifyr.entity.Notification;
import com.yash.Notifyr.entity.NotificationChannel;
import com.yash.Notifyr.entity.NotificationStatus;
import com.yash.Notifyr.entity.OutboxStatus;
import com.yash.Notifyr.exception.CampaignAlreadySentException;
import com.yash.Notifyr.provider.EmailProvider;
import com.yash.Notifyr.repository.CampaignRepository;
import com.yash.Notifyr.repository.NotificationRepository;
import com.yash.Notifyr.repository.OutboxEventRepository;
import com.yash.Notifyr.scheduler.NotificationRecoveryJob;
import com.yash.Notifyr.service.CampaignService;
import com.yash.Notifyr.service.NotificationService;
import com.yash.Notifyr.service.RecipientService;
import com.yash.Notifyr.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Delivery guarantees under duplicates, concurrency, broker outage and worker crashes, against real
// PostgreSQL / RabbitMQ / Redis containers. Has its own Spring context (and containers) because it
// pauses the broker and spies on the email provider.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReliabilityIntegrationTest {

    @MockitoSpyBean private EmailProvider emailProvider;

    @Autowired private NotificationService notificationService;
    @Autowired private CampaignService campaignService;
    @Autowired private TemplateService templateService;
    @Autowired private RecipientService recipientService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private NotificationRecoveryJob recoveryJob;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitMQContainer rabbitContainer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void duplicateMessagesForOneNotificationDeliverItOnce() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        Long id = notificationService.sendNotification(
                new NotificationRequest(email, "Dup", "Body"), "dup-user", null).getId();

        // 10 extra copies of the message race the original one through the 4 worker threads
        NotificationMessage duplicate = new NotificationMessage(id, email, "Dup", "Body", 0);
        runConcurrently(10, () -> {
            rabbitTemplate.convertAndSend("notification.exchange", "notification.route", duplicate);
            return null;
        });

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                        .isEqualTo(NotificationStatus.SIMULATED));
        // give the remaining duplicates time to be consumed, then check the provider was called once
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(emailProvider, times(1)).send(eq(email), anyString(), anyString()));
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOneNotification() throws Exception {
        String key = UUID.randomUUID().toString();
        NotificationRequest request = new NotificationRequest("idem@example.com", "Once", "Only once");

        List<Long> ids = runConcurrently(20, () ->
                notificationService.sendNotification(request, "idem-user", key).getId());

        assertThat(Set.copyOf(ids)).hasSize(1);
        long rows = notificationRepository.findAll().stream()
                .filter(n -> key.equals(n.getIdempotencyKey()))
                .count();
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void concurrentSendsOfOneCampaignDispatchItExactlyOnce() throws Exception {
        String tag = "race-" + UUID.randomUUID();
        Long templateId = templateService.create(new TemplateRequest("tpl-" + tag, "Hi {{name}}", "Body")).getId();
        for (int i = 0; i < 3; i++) {
            recipientService.create(new RecipientRequest("r" + i, tag + "-" + i + "@example.com", null, null,
                    null, null, List.of(tag)));
        }
        Long campaignId = campaignService.create(new CampaignRequest("Race", null, NotificationChannel.EMAIL,
                templateId, null, null, List.of(tag), Map.of(), null), "race-admin").getId();

        AtomicInteger rejected = new AtomicInteger();
        List<Boolean> results = runConcurrently(10, () -> {
            try {
                campaignService.send(campaignId);
                return true;
            } catch (CampaignAlreadySentException e) {
                rejected.incrementAndGet();
                return false;
            }
        });

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(9);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(campaignRepository.findById(campaignId).orElseThrow().getStatus())
                        .isEqualTo(CampaignStatus.COMPLETED));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Notification> fannedOut = notificationRepository.findAll().stream()
                    .filter(n -> campaignId.equals(n.getCampaignId()))
                    .toList();
            assertThat(fannedOut).hasSize(3);
            assertThat(fannedOut).allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SIMULATED));
        });
    }

    @Test
    void notificationsAcceptedDuringABrokerOutageAreDeliveredAfterwards() {
        pauseBroker();
        Long id;
        try {
            // the API keeps working: the notification and its message are committed to PostgreSQL
            id = notificationService.sendNotification(
                    new NotificationRequest("outage@example.com", "Outage", "Body"), "outage-user", null).getId();

            // the relay notices the unreachable broker (confirm/heartbeat timeouts) and backs off
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(
                    outboxEventRepository.findAll().stream()
                            .anyMatch(e -> e.getAggregateId().equals(id) && e.getStatus() == OutboxStatus.PENDING
                                    && e.getAttempts() >= 1)).isTrue());
            assertThat(notificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.QUEUED);
        } finally {
            unpauseBroker();
        }

        await().atMost(Duration.ofSeconds(120)).untilAsserted(() ->
                assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                        .isEqualTo(NotificationStatus.SIMULATED));
    }

    @Test
    void notificationsAbandonedByACrashedWorkerOrALostMessageAreRecovered() {
        // a worker claimed this one and died: PROCESSING with an expired lease
        Long abandoned = insertNotification(NotificationStatus.PROCESSING, "abandoned@example.com", 10);
        // this one's broker message was lost: QUEUED for 20 minutes with nothing in the outbox
        Long lost = insertNotification(NotificationStatus.QUEUED, "lost@example.com", 20);

        recoveryJob.recover();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Notification first = notificationRepository.findById(abandoned).orElseThrow();
            assertThat(first.getStatus()).isEqualTo(NotificationStatus.SIMULATED);
            assertThat(first.getRetryCount()).isEqualTo(1);   // the crashed attempt counts
            assertThat(notificationRepository.findById(lost).orElseThrow().getStatus())
                    .isEqualTo(NotificationStatus.SIMULATED);
        });
    }

    // regression: a stale copy of an earlier attempt (e.g. redelivered after a retry was scheduled) used to be
    // able to claim the notification and skip the retry delay
    @Test
    void messagesForAnEarlierAttemptDoNotTriggerDelivery() throws Exception {
        String email = "attempt-" + UUID.randomUUID() + "@example.com";
        Long id = insertNotification(NotificationStatus.QUEUED, email, 0);
        jdbcTemplate.update("UPDATE notifications SET retry_count = 1 WHERE id = ?", id);   // waiting for retry #1

        rabbitTemplate.convertAndSend("notification.exchange", "notification.route",
                new NotificationMessage(id, email, "Recover", "Body", 0));   // copy of attempt 0

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(notificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.QUEUED);
            verify(emailProvider, times(0)).send(eq(email), anyString(), anyString());
        });

        rabbitTemplate.convertAndSend("notification.exchange", "notification.route",
                new NotificationMessage(id, email, "Recover", "Body", 1));   // the current attempt

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                        .isEqualTo(NotificationStatus.SIMULATED));
        verify(emailProvider, times(1)).send(eq(email), anyString(), anyString());
    }

    // regression: a campaign whose dispatch failed without being able to record FAILED stayed RUNNING forever
    @Test
    void campaignStuckInRunningIsDispatchedAgainByTheRecoveryJob() {
        String tag = "stuck-" + UUID.randomUUID();
        Long templateId = templateService.create(new TemplateRequest("tpl-" + tag, "Hi {{name}}", "Body")).getId();
        recipientService.create(new RecipientRequest("stuck", tag + "@example.com", null, null, null, null, List.of(tag)));
        Long campaignId = campaignService.create(new CampaignRequest("Stuck", null, NotificationChannel.EMAIL,
                templateId, null, null, List.of(tag), Map.of(), null), "stuck-admin").getId();
        // RUNNING for 20 minutes with no dispatch message pending: the state left behind by a lost dispatch
        jdbcTemplate.update("UPDATE campaigns SET status = 'RUNNING', updated_at = now() - interval '20 minutes' WHERE id = ?",
                campaignId);

        recoveryJob.recover();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(campaignRepository.findById(campaignId).orElseThrow().getStatus())
                    .isEqualTo(CampaignStatus.COMPLETED);
            assertThat(notificationRepository.findAll().stream()
                    .filter(n -> campaignId.equals(n.getCampaignId())).count()).isEqualTo(1);
        });
    }

    // ---------- helpers ----------

    private Long insertNotification(NotificationStatus status, String email, int minutesAgo) {
        Notification notification = notificationRepository.save(Notification.builder()
                .recipientEmail(email).subject("Recover").message("Body")
                .channel(NotificationChannel.EMAIL).status(status).owner("recovery-user")
                .build());
        jdbcTemplate.update("UPDATE notifications SET updated_at = now() - make_interval(mins => ?) WHERE id = ?",
                minutesAgo, notification.getId());
        return notification.getId();
    }

    private void pauseBroker() {
        rabbitContainer.getDockerClient().pauseContainerCmd(rabbitContainer.getContainerId()).exec();
    }

    private void unpauseBroker() {
        rabbitContainer.getDockerClient().unpauseContainerCmd(rabbitContainer.getContainerId()).exec();
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
