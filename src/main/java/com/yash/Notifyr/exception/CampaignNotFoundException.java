package com.yash.Notifyr.exception;

public class CampaignNotFoundException extends RuntimeException {
    public CampaignNotFoundException(Long id) {
        super("Campaign with ID " + id + " not found");
    }
}
