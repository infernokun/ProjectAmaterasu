package com.infernokun.amaterasu.exceptions;

public class LabActionException  extends RuntimeException {
    public LabActionException(String message) {
        super(message);
    }

    public LabActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
