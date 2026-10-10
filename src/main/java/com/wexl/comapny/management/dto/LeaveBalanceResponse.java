package com.wexl.comapny.management.dto;

public record LeaveBalanceResponse(
		Long id,
		Long employeeId,
		Long leaveTypeId,
		String leaveTypeName,
		int year,
		int totalDays,
		int usedDays,
		int availableDays) {
}
