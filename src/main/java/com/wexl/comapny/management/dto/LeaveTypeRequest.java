package com.wexl.comapny.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LeaveTypeRequest(
		@NotBlank @Size(max = 50) String name,
		@Size(max = 255) String description,
		@NotNull @PositiveOrZero @Max(366) Integer defaultDays,
		Boolean active) {
}
