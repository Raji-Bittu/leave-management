package com.wexl.comapny.management.controller;

import com.wexl.comapny.management.dto.ApplyLeaveRequest;
import com.wexl.comapny.management.dto.LeaveBalanceResponse;
import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.security.CurrentUserService;
import com.wexl.comapny.management.service.LeaveBalanceService;
import com.wexl.comapny.management.service.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Self-service endpoints for the logged-in employee (managers use them for their own leave too). */
@RestController
@RequestMapping("/api/employees/me")
@RequiredArgsConstructor
public class EmployeeController {

	private final LeaveRequestService leaveRequestService;
	private final LeaveBalanceService leaveBalanceService;
	private final CurrentUserService currentUserService;
	private final Clock clock;

	@GetMapping("/leave-balances")
	public List<LeaveBalanceResponse> myBalances(@RequestParam(required = false) Integer year) {
		int effectiveYear = year != null ? year : LocalDate.now(clock).getYear();
		return leaveBalanceService.findBalances(currentUserService.currentEmployeeId(), effectiveYear);
	}

	@PostMapping("/leave-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public LeaveRequestResponse apply(@Valid @RequestBody ApplyLeaveRequest request) {
		return leaveRequestService.apply(currentUserService.currentEmployeeId(), request);
	}

	@GetMapping("/leave-requests")
	public List<LeaveRequestResponse> myRequests() {
		return leaveRequestService.findMyRequests(currentUserService.currentEmployeeId());
	}

	@PutMapping("/leave-requests/{id}/cancel")
	public LeaveRequestResponse cancel(@PathVariable Long id) {
		return leaveRequestService.cancel(currentUserService.currentEmployeeId(), id);
	}
}
