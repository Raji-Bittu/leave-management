package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.ApplyLeaveRequest;
import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.LeaveBalance;
import com.wexl.comapny.management.entity.LeaveRequest;
import com.wexl.comapny.management.entity.LeaveStatus;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.exception.InsufficientLeaveBalanceException;
import com.wexl.comapny.management.exception.InvalidLeaveDatesException;
import com.wexl.comapny.management.exception.InvalidLeaveStateException;
import com.wexl.comapny.management.exception.OverlappingLeaveException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.mapper.LeaveMapper;
import com.wexl.comapny.management.repository.LeaveBalanceRepository;
import com.wexl.comapny.management.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

	private static final EnumSet<LeaveStatus> ACTIVE_STATUSES = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

	private final LeaveRequestRepository leaveRequestRepository;
	private final LeaveBalanceRepository leaveBalanceRepository;
	private final EmployeeService employeeService;
	private final LeaveTypeService leaveTypeService;
	private final Clock clock;

	@Transactional
	public LeaveRequestResponse apply(Long employeeId, ApplyLeaveRequest request) {
		// Lock the employee first: without it, two simultaneous submissions both pass the overlap and
		// balance checks below (neither sees the other's uncommitted row) and both get saved.
		Employee employee = employeeService.getEmployeeForUpdate(employeeId);
		if (employee.getManager() == null) {
			throw new BusinessRuleException("You have no manager assigned, so your leave cannot be approved; contact an admin");
		}
		if (!employee.getManager().isActive()) {
			throw new BusinessRuleException("Your manager is inactive, so your leave cannot be approved; contact an admin");
		}
		LeaveType type = leaveTypeService.getLeaveType(request.leaveTypeId());
		if (!type.isActive()) {
			throw new BusinessRuleException("Leave type " + type.getId() + " is inactive");
		}

		LocalDate start = request.startDate();
		LocalDate end = request.endDate();
		validateDates(start, end);
		int days = countWorkingDays(start, end);
		if (days == 0) {
			throw new InvalidLeaveDatesException("The requested period contains no working days");
		}

		if (leaveRequestRepository.existsOverlapping(employeeId, start, end, ACTIVE_STATUSES)) {
			throw new OverlappingLeaveException();
		}

		int year = start.getYear();
		LeaveBalance balance = leaveBalanceRepository
				.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year)
				.orElseThrow(() -> new InsufficientLeaveBalanceException(days, 0));
		// Pending requests reserve days so an employee cannot queue more leave than they own.
		long pending = leaveRequestRepository.sumPendingDays(employeeId, type.getId(),
				LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
		int available = (int) (balance.getAvailableDays() - pending);
		if (days > available) {
			throw new InsufficientLeaveBalanceException(days, Math.max(available, 0));
		}

		LeaveRequest leave = new LeaveRequest();
		leave.setEmployee(employee);
		leave.setLeaveType(type);
		leave.setStartDate(start);
		leave.setEndDate(end);
		leave.setDays(days);
		leave.setReason(request.reason());
		leave.setStatus(LeaveStatus.PENDING);
		return LeaveMapper.toResponse(leaveRequestRepository.save(leave));
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestResponse> findMyRequests(Long employeeId) {
		return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
				.map(LeaveMapper::toResponse)
				.toList();
	}

	@Transactional
	public LeaveRequestResponse cancel(Long employeeId, Long requestId) {
		LeaveRequest leave = getRequest(requestId);
		if (!Objects.equals(leave.getEmployee().getId(), employeeId)) {
			throw new AccessDeniedException("You can only cancel your own leave requests");
		}
		switch (leave.getStatus()) {
			case PENDING -> leave.setStatus(LeaveStatus.CANCELLED);
			case APPROVED -> {
				if (!leave.getStartDate().isAfter(today())) {
					throw new InvalidLeaveStateException("Approved leave can only be cancelled before it starts");
				}
				employeeService.getEmployeeForUpdate(employeeId);
				LeaveBalance balance = getBalance(leave);
				balance.setUsedDays(Math.max(0, balance.getUsedDays() - leave.getDays()));
				leave.setStatus(LeaveStatus.CANCELLED);
			}
			default -> throw new InvalidLeaveStateException(
					"Leave request in status " + leave.getStatus() + " cannot be cancelled");
		}
		return LeaveMapper.toResponse(leave);
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestResponse> findTeamRequests(Long managerId, LeaveStatus status) {
		List<LeaveRequest> requests = status == null
				? leaveRequestRepository.findByEmployeeManagerIdOrderByStartDateDesc(managerId)
				: leaveRequestRepository.findByEmployeeManagerIdAndStatusOrderByStartDateDesc(managerId, status);
		return requests.stream().map(LeaveMapper::toResponse).toList();
	}

	@Transactional
	public LeaveRequestResponse approve(Long managerId, Long requestId) {
		LeaveRequest leave = getPendingRequestForManager(managerId, requestId);
		// Same lock as apply(): moving days from "pending" to "used" while apply() is between reading the
		// balance and summing pending days would make apply() miss this request's days entirely.
		employeeService.getEmployeeForUpdate(leave.getEmployee().getId());
		LeaveBalance balance = getBalance(leave);
		if (leave.getDays() > balance.getAvailableDays()) {
			throw new InsufficientLeaveBalanceException(leave.getDays(), balance.getAvailableDays());
		}
		balance.setUsedDays(balance.getUsedDays() + leave.getDays());
		decide(leave, managerId, LeaveStatus.APPROVED);
		return LeaveMapper.toResponse(leave);
	}

	@Transactional
	public LeaveRequestResponse reject(Long managerId, Long requestId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new BusinessRuleException("A rejection reason is required");
		}
		LeaveRequest leave = getPendingRequestForManager(managerId, requestId);
		leave.setRejectionReason(reason.trim());
		decide(leave, managerId, LeaveStatus.REJECTED);
		return LeaveMapper.toResponse(leave);
	}

	/** Counts Monday-Friday days in the inclusive range; public holidays are not modelled. */
	static int countWorkingDays(LocalDate start, LocalDate end) {
		int days = 0;
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			DayOfWeek dow = date.getDayOfWeek();
			if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
				days++;
			}
		}
		return days;
	}

	private void validateDates(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw new InvalidLeaveDatesException("End date must be on or after the start date");
		}
		if (start.isBefore(today())) {
			throw new InvalidLeaveDatesException("Leave cannot be applied for past dates");
		}
		if (start.getYear() != end.getYear()) {
			throw new InvalidLeaveDatesException(
					"A leave request cannot span two calendar years; split it into separate requests");
		}
	}

	private LeaveRequest getPendingRequestForManager(Long managerId, Long requestId) {
		LeaveRequest leave = getRequest(requestId);
		Employee manager = leave.getEmployee().getManager();
		if (manager == null || !Objects.equals(manager.getId(), managerId)) {
			throw new AccessDeniedException("Only the employee's manager can approve or reject this leave request");
		}
		if (leave.getStatus() != LeaveStatus.PENDING) {
			throw new InvalidLeaveStateException(
					"Only pending leave requests can be decided; current status is " + leave.getStatus());
		}
		return leave;
	}

	private void decide(LeaveRequest leave, Long managerId, LeaveStatus status) {
		leave.setStatus(status);
		leave.setDecidedBy(employeeService.getEmployee(managerId));
		leave.setDecidedAt(clock.instant());
	}

	private LeaveRequest getRequest(Long requestId) {
		return leaveRequestRepository.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("Leave request", requestId));
	}

	private LeaveBalance getBalance(LeaveRequest leave) {
		return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
						leave.getEmployee().getId(), leave.getLeaveType().getId(), leave.getStartDate().getYear())
				.orElseThrow(() -> new BusinessRuleException("No leave balance configured for this leave type and year"));
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}
}
