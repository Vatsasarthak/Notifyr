package com.yash.Notifyr.dto;

import com.yash.Notifyr.entity.NotificationChannel;
import com.yash.Notifyr.entity.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String recipientEmail;
    private NotificationStatus status;
    private String subject;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private NotificationChannel channel;
    private Integer retryCount;
    private Long campaignId;
    private LocalDateTime sentAt;
}
