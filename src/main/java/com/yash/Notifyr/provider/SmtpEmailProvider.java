package com.yash.Notifyr.provider;

import com.yash.Notifyr.security.PiiMasking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification", name = "mock-provider", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailProvider implements EmailProvider{

    private final JavaMailSender mailSender;

    @Value("${notification.mail.from:no-reply@notifyr.com}")
    private String fromAddress;

    @Override
    public void send(String to, String subject, String body) throws Exception {

        if (to == null || to.isBlank()) {
            throw new PermanentDeliveryException("Recipient email address is missing");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);

        log.info("Email sent to {} with subject: {}", PiiMasking.email(to), subject);
    }
}
