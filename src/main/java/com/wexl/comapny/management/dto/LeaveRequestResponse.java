package com.wexl.comapny.management.dto;

import com.wexl.comapny.management.entity.LeaveStatus;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(
		Long id,
		Long employeeId,
		String employeeName,
		Long leaveTypeId,
		String leaveTypeName,
		LocalDate startDate,
		LocalDate endDate,
		int days,
		String reason,
		LeaveStatus status,
		String rejectionReason,
		Long decidedById,
		Instant decidedAt,
		Instant createdAt) {
}
