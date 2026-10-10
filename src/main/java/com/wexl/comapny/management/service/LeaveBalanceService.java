package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.ConfigureLeaveBalanceRequest;
import com.wexl.comapny.management.dto.LeaveBalanceResponse;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.LeaveBalance;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.mapper.LeaveMapper;
import com.wexl.comapny.management.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

	private final LeaveBalanceRepository leaveBalanceRepository;
	private final EmployeeService employeeService;
	private final LeaveTypeService leaveTypeService;

	/** Creates the balance for (employee, type, year) or updates its total if it already exists. */
	@Transactional
	public LeaveBalanceResponse configure(Long employeeId, ConfigureLeaveBalanceRequest request) {
		Employee employee = employeeService.getEmployee(employeeId);
		LeaveType type = leaveTypeService.getLeaveType(request.leaveTypeId());
		if (!type.isActive()) {
			throw new BusinessRuleException("Leave type " + type.getId() + " is inactive");
		}
		LeaveBalance balance = leaveBalanceRepository
				.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), request.year())
				.orElseGet(() -> {
					LeaveBalance created = new LeaveBalance();
					created.setEmployee(employee);
					created.setLeaveType(type);
					created.setYear(request.year());
					created.setUsedDays(0);
					return created;
				});
		if (request.totalDays() < balance.getUsedDays()) {
			throw new BusinessRuleException("Total days (" + request.totalDays()
					+ ") cannot be less than days already used (" + balance.getUsedDays() + ")");
		}
		balance.setTotalDays(request.totalDays());
		return LeaveMapper.toResponse(leaveBalanceRepository.save(balance));
	}

	@Transactional(readOnly = true)
	public List<LeaveBalanceResponse> findBalances(Long employeeId, int year) {
		return leaveBalanceRepository.findByEmployeeIdAndYearOrderByLeaveTypeName(employeeId, year).stream()
				.map(LeaveMapper::toResponse)
				.toList();
	}
}
