package com.yash.Notifyr.exception;

public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
