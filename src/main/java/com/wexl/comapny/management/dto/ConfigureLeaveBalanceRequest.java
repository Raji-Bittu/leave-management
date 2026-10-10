package com.wexl.comapny.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ConfigureLeaveBalanceRequest(
		@NotNull Long leaveTypeId,
		@NotNull @Min(2000) @Max(2100) Integer year,
		@NotNull @PositiveOrZero @Max(366) Integer totalDays) {
}
