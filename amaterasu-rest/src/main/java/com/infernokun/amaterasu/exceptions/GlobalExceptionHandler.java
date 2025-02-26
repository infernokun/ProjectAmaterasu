package com.infernokun.amaterasu.exceptions;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
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
                .message("FileUploadException: " + ex.getMessage())
                .data("Error encountered during file upload")
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LabReadinessException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleLabReadinessException(
            LabReadinessException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("LabReadinessException: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RemoteCommandException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleRemoteCommandException(RemoteCommandException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("RemoteCommandException: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.NOT_FOUND.value())
                .message("ResourceNotFoundException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<ApiResponse<String>> handleWrongPasswordException(WrongPasswordException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("WrongPasswordException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthFailedException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleAuthFailedException(AuthFailedException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("AuthFailedException: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleTokenException(TokenException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("TokenException: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CryptoException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleTokenException(CryptoException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("CryptoException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServerTypeException.class)
    public ResponseEntity<ApiResponse<LabActionResult>> handleTokenException(ServerTypeException ex) {
        ApiResponse<LabActionResult> response = ApiResponse.<LabActionResult>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("ServerTypeException: " + ex.getMessage())
                .data(new LabActionResult(false, null, ex.getMessage()))
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex) {
        ApiResponse<?> response = ApiResponse.<String>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("RuntimeException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(Exception ex) {
        ApiResponse<?> response = ApiResponse.<String>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Exception: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}