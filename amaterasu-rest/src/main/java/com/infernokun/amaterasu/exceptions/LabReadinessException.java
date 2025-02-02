package com.infernokun.amaterasu.exceptions;

public class LabReadinessException extends RuntimeException {
    public LabReadinessException(String message) {
        super(message);
    }

  public LabReadinessException(String message, Throwable cause) {
    super(message, cause);
  }
}
