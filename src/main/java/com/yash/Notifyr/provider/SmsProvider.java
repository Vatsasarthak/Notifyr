package com.yash.Notifyr.provider;

public interface SmsProvider {

    void send(String phoneNumber, String message) throws Exception;

    // true when the provider only pretends to deliver (development mock mode)
    default boolean isSimulated() {
        return false;
    }
}
