package com.banking.common.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response returned when a request fails")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "401")
        int status,

        @Schema(description = "Short error reason", example = "Unauthorized")
        String error,

        @Schema(description = "Human-readable error message", example = "Invalid credentials")
        String message
) {}
