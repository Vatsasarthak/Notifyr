package com.yash.Notifyr.exception;

import com.yash.Notifyr.entity.NotificationStatus;

public class InvalidNotificationStateException extends RuntimeException {
    public InvalidNotificationStateException(Long id, NotificationStatus currentStatus, String rule) {
        super("Notification " + id + " is " + currentStatus + ": " + rule);
    }
}
