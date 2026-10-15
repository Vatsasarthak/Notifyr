package com.yash.Notifyr.exception;

public class AdminRegistrationNotAllowedException extends RuntimeException {
    public AdminRegistrationNotAllowedException() {
        super("Only an authenticated ADMIN can register another ADMIN user");
    }
}
