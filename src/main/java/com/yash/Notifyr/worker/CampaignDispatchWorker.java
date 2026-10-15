package com.yash.Notifyr.worker;

import com.yash.Notifyr.config.CorrelationIdFilter;
import com.yash.Notifyr.dto.CampaignDispatchMessage;
import com.yash.Notifyr.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignDispatchWorker {

    private final CampaignService campaignService;

    @RabbitListener(queues = "${notification.campaign-dispatch.queue}")
    public void handleCampaignDispatch(CampaignDispatchMessage message,
                                       @Header(name = CorrelationIdFilter.HEADER, required = false) String correlationId){
        CorrelationIdFilter.putFromMessage(correlationId);
        log.info("Received dispatch trigger for campaign {}", message.getCampaignId());

        try{
            campaignService.processDispatch(message.getCampaignId());
        }catch(Exception e){
            // the campaign is already marked FAILED; redelivering would not help
            log.error("Failed to process dispatch for campaign {}: {}", message.getCampaignId(), e.getMessage());
        }finally{
            CorrelationIdFilter.clear();
        }
    }
}
