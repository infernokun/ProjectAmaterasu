package com.infernokun.amaterasu.exceptions;

public class RemoteCommandException extends RuntimeException {
    public RemoteCommandException(String message) {
        super(message);
    }

    public RemoteCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
