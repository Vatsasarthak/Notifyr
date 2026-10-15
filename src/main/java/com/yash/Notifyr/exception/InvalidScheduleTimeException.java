package com.yash.Notifyr.exception;

public class InvalidScheduleTimeException extends RuntimeException {
    public InvalidScheduleTimeException() {
        super("scheduledTime must be in the future");
    }

}
