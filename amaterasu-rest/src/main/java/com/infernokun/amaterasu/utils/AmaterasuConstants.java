package com.infernokun.amaterasu.utils;

import com.infernokun.amaterasu.models.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class AmaterasuConstants {
    public static String DEFAULT_SURROUND_TAG = "CTF{x}";

    public static  <T> ResponseEntity<ApiResponse<T>> buildSuccessResponse(
            String message, T data, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .code(status.value())
                        .message(message)
                        .data(data)
                        .build());
    }
}
