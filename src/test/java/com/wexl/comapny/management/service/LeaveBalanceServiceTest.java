package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.ConfigureLeaveBalanceRequest;
import com.wexl.comapny.management.dto.LeaveBalanceResponse;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.LeaveBalance;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.repository.LeaveBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceTest {

	@Mock
	private LeaveBalanceRepository leaveBalanceRepository;
	@Mock
	private EmployeeService employeeService;
	@Mock
	private LeaveTypeService leaveTypeService;
	@InjectMocks
	private LeaveBalanceService service;

	private Employee employee;
	private LeaveType annual;

	@BeforeEach
	void setUp() {
		employee = new Employee();
		employee.setId(2L);
		employee.setRole(Role.EMPLOYEE);
		annual = new LeaveType();
		annual.setId(10L);
		annual.setName("Annual");
		annual.setActive(true);
	}

	@Test
	void createsNewBalanceWithNoUsedDays() {
		when(employeeService.getEmployee(2L)).thenReturn(employee);
		when(leaveTypeService.getLeaveType(10L)).thenReturn(annual);
		when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(2L, 10L, 2026)).thenReturn(Optional.empty());
		when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));

		LeaveBalanceResponse response = service.configure(2L, new ConfigureLeaveBalanceRequest(10L, 2026, 20));

		assertThat(response.totalDays()).isEqualTo(20);
		assertThat(response.usedDays()).isZero();
		assertThat(response.availableDays()).isEqualTo(20);
		assertThat(response.leaveTypeName()).isEqualTo("Annual");
	}

	@Test
	void updatesExistingBalanceKeepingUsedDays() {
		LeaveBalance existing = balance(5);
		when(employeeService.getEmployee(2L)).thenReturn(employee);
		when(leaveTypeService.getLeaveType(10L)).thenReturn(annual);
		when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(2L, 10L, 2026))
				.thenReturn(Optional.of(existing));
		when(leaveBalanceRepository.save(existing)).thenReturn(existing);

		LeaveBalanceResponse response = service.configure(2L, new ConfigureLeaveBalanceRequest(10L, 2026, 25));

		assertThat(response.totalDays()).isEqualTo(25);
		assertThat(response.usedDays()).isEqualTo(5);
		assertThat(response.availableDays()).isEqualTo(20);
	}

	@Test
	void totalCannotDropBelowUsedDays() {
		when(employeeService.getEmployee(2L)).thenReturn(employee);
		when(leaveTypeService.getLeaveType(10L)).thenReturn(annual);
		when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(2L, 10L, 2026))
				.thenReturn(Optional.of(balance(8)));

		assertThatThrownBy(() -> service.configure(2L, new ConfigureLeaveBalanceRequest(10L, 2026, 5)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("already used");
		verify(leaveBalanceRepository, never()).save(any());
	}

	@Test
	void cannotConfigureInactiveLeaveType() {
		annual.setActive(false);
		when(employeeService.getEmployee(2L)).thenReturn(employee);
		when(leaveTypeService.getLeaveType(10L)).thenReturn(annual);

		assertThatThrownBy(() -> service.configure(2L, new ConfigureLeaveBalanceRequest(10L, 2026, 5)))
				.isInstanceOf(BusinessRuleException.class);
	}

	private LeaveBalance balance(int used) {
		LeaveBalance balance = new LeaveBalance();
		balance.setId(1L);
		balance.setEmployee(employee);
		balance.setLeaveType(annual);
		balance.setYear(2026);
		balance.setTotalDays(20);
		balance.setUsedDays(used);
		return balance;
	}
}
