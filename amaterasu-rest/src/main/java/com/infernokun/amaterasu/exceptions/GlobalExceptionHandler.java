package com.infernokun.amaterasu.exceptions;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.LabActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChallengeNotAnsweredException.class)
    public ResponseEntity<ApiResponse<String>> handleChallengeNotAnsweredException(
            ChallengeNotAnsweredException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("ChallengeNotAnsweredException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ExceptionHandler(LabActionException.class)
    public ResponseEntity<ApiResponse<String>> handleLabActionException(
            LabActionException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("LabActionException: " + ex.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponse<String>> handleFileUploadException(
            FileUploadException ex) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("FileUploadException: " + ex.getMessage())
                .data(null)
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

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleTokenException(TokenException ex) {
        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("TokenException: " + ex.getMessage())
                .data(false)
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        LOGGER.warn("Optimistic locking conflict: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.<String>builder()
                .code(HttpStatus.CONFLICT.value())
                .message("The resource was modified concurrently, please retry.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoResourceFoundException(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " /" + ex.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<String>> handleBadRequest(RuntimeException ex) {
        LOGGER.warn("Rejected request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    private static ResponseEntity<ApiResponse<String>> buildResponse(HttpStatus status, String message) {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(status.value())
                .message(message)
                .data(null)
                .build();
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<String>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ApiResponse<String>> handleResourceInUse(ResourceInUseException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiResponse<String>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex) {
        return internalError(ex);
    }

    // Internal details stay in the log; the client gets a reference it can quote
    private static ResponseEntity<ApiResponse<?>> internalError(Exception ex) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8);
        LOGGER.error("Unhandled exception [{}]", reference, ex);
        ApiResponse<?> response = ApiResponse.<String>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Internal server error (reference " + reference + ")")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return new ResponseEntity<>(ApiResponse.<String>builder()
                    .code(status.value())
                    .message(ex.getMessage())
                    .data(null)
                    .build(), status);
        }
        return internalError(ex);
    }
}