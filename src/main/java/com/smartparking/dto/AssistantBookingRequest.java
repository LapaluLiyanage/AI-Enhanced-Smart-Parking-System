package com.smartparking.dto;

import jakarta.validation.constraints.NotBlank;

public record AssistantBookingRequest(@NotBlank String message) {
}
