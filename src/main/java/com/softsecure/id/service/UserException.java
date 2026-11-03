package com.softsecure.id.service;

/** Only deliberately authored, non-sensitive messages may cross into the UI. */
public final class UserException extends RuntimeException {
    public UserException(String message) {
        super(message);
    }
}
