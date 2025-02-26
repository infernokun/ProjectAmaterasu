package com.infernokun.amaterasu.exceptions;

public class ServerTypeException extends RuntimeException {
    public ServerTypeException(String message) {
        super(message);
    }

    public ServerTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
