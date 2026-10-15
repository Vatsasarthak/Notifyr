package com.yash.Notifyr.dto;

import com.yash.Notifyr.entity.CampaignStatus;
import com.yash.Notifyr.entity.NotificationStatus;

import java.util.Map;

public record CampaignStatsResponse(Long campaignId, CampaignStatus campaignStatus, long totalNotifications,
                                    Map<NotificationStatus, Long> notificationsByStatus) {
}
