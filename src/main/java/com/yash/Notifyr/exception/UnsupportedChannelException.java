package com.yash.Notifyr.exception;

import com.yash.Notifyr.entity.NotificationChannel;

public class UnsupportedChannelException extends RuntimeException {
    public UnsupportedChannelException(NotificationChannel channel){
        super("Unsupported notification channel: " + channel);
    }
}
