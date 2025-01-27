package com.infernokun.amaterasu.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code; // HTTP status code
    private String message; // Response message
    private T data; // Generic type for additional data
}
