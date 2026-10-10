package com.wexl.comapny.management.controller;

import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.dto.RejectLeaveRequest;
import com.wexl.comapny.management.entity.LeaveStatus;
import com.wexl.comapny.management.security.CurrentUserService;
import com.wexl.comapny.management.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manager/leave-requests")
@RequiredArgsConstructor
public class ManagerController {

	private final LeaveRequestService leaveRequestService;
	private final CurrentUserService currentUserService;

	@GetMapping
	public List<LeaveRequestResponse> teamRequests(@RequestParam(required = false) LeaveStatus status) {
		return leaveRequestService.findTeamRequests(currentUserService.currentEmployeeId(), status);
	}

	@PutMapping("/{id}/approve")
	public LeaveRequestResponse approve(@PathVariable Long id) {
		return leaveRequestService.approve(currentUserService.currentEmployeeId(), id);
	}

	@PutMapping("/{id}/reject")
	public LeaveRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectLeaveRequest request) {
		return leaveRequestService.reject(currentUserService.currentEmployeeId(), id, request.reason());
	}
}
