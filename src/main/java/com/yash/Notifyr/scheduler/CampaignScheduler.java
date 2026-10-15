package com.yash.Notifyr.scheduler;

import com.yash.Notifyr.entity.Campaign;
import com.yash.Notifyr.entity.CampaignStatus;
import com.yash.Notifyr.repository.CampaignRepository;
import com.yash.Notifyr.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;

    @Scheduled(fixedRate = 30000) // run each 30s
    public void processScheduledCampaigns(){

        List<Campaign> dueCampaigns = campaignRepository.findByStatusAndScheduledTimeLessThanEqual(
                CampaignStatus.SCHEDULED,
                LocalDateTime.now()
        );

        if(dueCampaigns.isEmpty()){
            return;
        }

        log.info("Found {} scheduled campaign(s) due for sending", dueCampaigns.size());

        for(Campaign campaign : dueCampaigns){
            try{
                campaignService.send(campaign.getId());
                log.info("Scheduled campaign {} triggered successfully", campaign.getId());
            }catch (Exception e){
                log.error("Error sending campaign with ID {}: {}", campaign.getId(), e.getMessage());
            }
        }
    }
}
