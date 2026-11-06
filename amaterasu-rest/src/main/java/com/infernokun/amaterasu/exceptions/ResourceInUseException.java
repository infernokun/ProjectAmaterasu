package com.infernokun.amaterasu.exceptions;

/** The request would remove something that is still in use, such as a server with labs running on it. */
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
