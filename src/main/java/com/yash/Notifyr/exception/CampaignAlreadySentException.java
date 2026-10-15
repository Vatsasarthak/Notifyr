package com.yash.Notifyr.exception;

import com.yash.Notifyr.entity.CampaignStatus;

public class CampaignAlreadySentException extends RuntimeException {
    public CampaignAlreadySentException(Long id, CampaignStatus currentStatus) {
        super("Campaign " + id + " cannot be sent - current status is " + currentStatus +
                " (only DRAFT or SCHEDULED campaigns can be sent)");
    }
}
