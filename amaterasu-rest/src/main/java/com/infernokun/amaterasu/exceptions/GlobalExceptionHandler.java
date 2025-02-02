package com.infernokun.amaterasu.exceptions;

import com.infernokun.amaterasu.models.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponse<String>> handleFileUploadException(
            FileUploadException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("File upload error: " + ex.getMessage())
                .data("Error encountered during file upload")
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LabReadinessException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleLabReadinessException(
            LabReadinessException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Lab readiness exception...: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RemoteCommandException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleRemoteCommandException(RemoteCommandException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Remote command error: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An error occurred: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}