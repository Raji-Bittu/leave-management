package com.wexl.comapny.management.mapper;

import com.wexl.comapny.management.dto.LeaveBalanceResponse;
import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.dto.LeaveTypeResponse;
import com.wexl.comapny.management.entity.LeaveBalance;
import com.wexl.comapny.management.entity.LeaveRequest;
import com.wexl.comapny.management.entity.LeaveType;

public final class LeaveMapper {

	private LeaveMapper() {
	}

	public static LeaveTypeResponse toResponse(LeaveType type) {
		return new LeaveTypeResponse(type.getId(), type.getName(), type.getDescription(),
				type.getDefaultDays(), type.isActive());
	}

	public static LeaveBalanceResponse toResponse(LeaveBalance balance) {
		return new LeaveBalanceResponse(
				balance.getId(),
				balance.getEmployee().getId(),
				balance.getLeaveType().getId(),
				balance.getLeaveType().getName(),
				balance.getYear(),
				balance.getTotalDays(),
				balance.getUsedDays(),
				balance.getAvailableDays());
	}

	public static LeaveRequestResponse toResponse(LeaveRequest request) {
		return new LeaveRequestResponse(
				request.getId(),
				request.getEmployee().getId(),
				request.getEmployee().getName(),
				request.getLeaveType().getId(),
				request.getLeaveType().getName(),
				request.getStartDate(),
				request.getEndDate(),
				request.getDays(),
				request.getReason(),
				request.getStatus(),
				request.getRejectionReason(),
				request.getDecidedBy() != null ? request.getDecidedBy().getId() : null,
				request.getDecidedAt(),
				request.getCreatedAt());
	}
}
