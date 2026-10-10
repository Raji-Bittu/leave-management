package com.wexl.comapny.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ApplyLeaveRequest(
		@NotNull Long leaveTypeId,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@Size(max = 500) String reason) {
}
