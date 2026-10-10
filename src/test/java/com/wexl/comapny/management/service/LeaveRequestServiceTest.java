package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.ApplyLeaveRequest;
import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.LeaveBalance;
import com.wexl.comapny.management.entity.LeaveRequest;
import com.wexl.comapny.management.entity.LeaveStatus;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.exception.InsufficientLeaveBalanceException;
import com.wexl.comapny.management.exception.InvalidLeaveDatesException;
import com.wexl.comapny.management.exception.InvalidLeaveStateException;
import com.wexl.comapny.management.exception.OverlappingLeaveException;
import com.wexl.comapny.management.repository.LeaveBalanceRepository;
import com.wexl.comapny.management.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

	/** Friday 9 October 2026. */
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
	private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 12);
	private static final LocalDate NEXT_FRIDAY = LocalDate.of(2026, 10, 16);

	@Mock
	private LeaveRequestRepository leaveRequestRepository;
	@Mock
	private LeaveBalanceRepository leaveBalanceRepository;
	@Mock
	private EmployeeService employeeService;
	@Mock
	private LeaveTypeService leaveTypeService;

	private LeaveRequestService service;

	private Employee manager;
	private Employee employee;
	private LeaveType annual;
	private LeaveBalance balance;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
		service = new LeaveRequestService(leaveRequestRepository, leaveBalanceRepository, employeeService,
				leaveTypeService, clock);

		manager = employee(1L, "Mona Manager", Role.MANAGER, null);
		employee = employee(2L, "Eve Employee", Role.EMPLOYEE, manager);

		annual = new LeaveType();
		annual.setId(10L);
		annual.setName("Annual");
		annual.setDefaultDays(20);
		annual.setActive(true);

		balance = new LeaveBalance();
		balance.setId(50L);
		balance.setEmployee(employee);
		balance.setLeaveType(annual);
		balance.setYear(2026);
		balance.setTotalDays(10);
		balance.setUsedDays(0);
	}

	@Nested
	class Apply {

		@Test
		void createsPendingRequestCountingWorkingDaysWithoutTouchingBalance() {
			stubEmployeeAndType();
			stubNoOverlap();
			stubBalance();
			stubPendingDays(0);
			when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

			LeaveRequestResponse response = service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY));

			assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
			assertThat(response.days()).isEqualTo(5);
			assertThat(response.employeeId()).isEqualTo(2L);
			assertThat(balance.getUsedDays()).isZero();
		}

		@Test
		void excludesWeekendsFromDayCount() {
			stubEmployeeAndType();
			stubNoOverlap();
			stubBalance();
			stubPendingDays(0);
			when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

			// Friday 16th to Monday 19th = Fri + Mon
			LeaveRequestResponse response = service.apply(2L, apply(NEXT_FRIDAY, NEXT_FRIDAY.plusDays(3)));

			assertThat(response.days()).isEqualTo(2);
		}

		@Test
		void allowsLeaveStartingToday() {
			stubEmployeeAndType();
			stubNoOverlap();
			stubBalance();
			stubPendingDays(0);
			when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

			assertThat(service.apply(2L, apply(TODAY, TODAY)).days()).isEqualTo(1);
		}

		@Test
		void rejectsPastDates() {
			stubEmployeeAndType();

			assertThatThrownBy(() -> service.apply(2L, apply(TODAY.minusDays(1), NEXT_FRIDAY)))
					.isInstanceOf(InvalidLeaveDatesException.class)
					.hasMessageContaining("past dates");
			verify(leaveRequestRepository, never()).save(any());
		}

		@Test
		void rejectsEndDateBeforeStartDate() {
			stubEmployeeAndType();

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_FRIDAY, NEXT_MONDAY)))
					.isInstanceOf(InvalidLeaveDatesException.class);
		}

		@Test
		void rejectsRequestSpanningTwoYears() {
			stubEmployeeAndType();

			assertThatThrownBy(() -> service.apply(2L,
					apply(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4))))
					.isInstanceOf(InvalidLeaveDatesException.class)
					.hasMessageContaining("calendar years");
		}

		@Test
		void rejectsPeriodWithOnlyWeekendDays() {
			stubEmployeeAndType();

			assertThatThrownBy(() -> service.apply(2L, apply(TODAY.plusDays(1), TODAY.plusDays(2))))
					.isInstanceOf(InvalidLeaveDatesException.class)
					.hasMessageContaining("no working days");
		}

		@Test
		void rejectsOverlappingRequest() {
			stubEmployeeAndType();
			when(leaveRequestRepository.existsOverlapping(eq(2L), eq(NEXT_MONDAY), eq(NEXT_FRIDAY), anyCollection()))
					.thenReturn(true);

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(OverlappingLeaveException.class);
			verify(leaveRequestRepository, never()).save(any());
		}

		@Test
		void rejectsWhenBalanceIsInsufficient() {
			balance.setTotalDays(3);
			stubEmployeeAndType();
			stubNoOverlap();
			stubBalance();
			stubPendingDays(0);

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(InsufficientLeaveBalanceException.class)
					.hasMessageContaining("requested 5")
					.hasMessageContaining("available 3");
			verify(leaveRequestRepository, never()).save(any());
		}

		@Test
		void pendingRequestsReserveBalance() {
			stubEmployeeAndType();
			stubNoOverlap();
			stubBalance();
			stubPendingDays(6);

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(InsufficientLeaveBalanceException.class)
					.hasMessageContaining("available 4");
		}

		@Test
		void rejectsWhenNoBalanceConfigured() {
			stubEmployeeAndType();
			stubNoOverlap();
			when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(2L, 10L, 2026))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(InsufficientLeaveBalanceException.class);
		}

		@Test
		void rejectsEmployeeWithoutManager() {
			employee.setManager(null);
			when(employeeService.getEmployee(2L)).thenReturn(employee);

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("no manager");
		}

		@Test
		void rejectsInactiveLeaveType() {
			annual.setActive(false);
			stubEmployeeAndType();

			assertThatThrownBy(() -> service.apply(2L, apply(NEXT_MONDAY, NEXT_FRIDAY)))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("inactive");
		}
	}

	@Nested
	class Approve {

		@Test
		void approvingDeductsBalance() {
			LeaveRequest leave = pendingRequest();
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));
			stubBalance();
			when(employeeService.getEmployee(1L)).thenReturn(manager);

			LeaveRequestResponse response = service.approve(1L, 100L);

			assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
			assertThat(response.decidedById()).isEqualTo(1L);
			assertThat(response.decidedAt()).isNotNull();
			assertThat(balance.getUsedDays()).isEqualTo(5);
			assertThat(balance.getAvailableDays()).isEqualTo(5);
		}

		@Test
		void onlyTheEmployeesManagerCanApprove() {
			LeaveRequest leave = pendingRequest();
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));

			assertThatThrownBy(() -> service.approve(99L, 100L))
					.isInstanceOf(AccessDeniedException.class);
			assertThat(leave.getStatus()).isEqualTo(LeaveStatus.PENDING);
			verifyNoInteractions(leaveBalanceRepository);
		}

		@Test
		void cannotApproveNonPendingRequest() {
			LeaveRequest leave = pendingRequest();
			leave.setStatus(LeaveStatus.CANCELLED);
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));

			assertThatThrownBy(() -> service.approve(1L, 100L))
					.isInstanceOf(InvalidLeaveStateException.class);
		}

		@Test
		void rechecksBalanceAtApprovalTime() {
			balance.setUsedDays(8);
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(pendingRequest()));
			stubBalance();

			assertThatThrownBy(() -> service.approve(1L, 100L))
					.isInstanceOf(InsufficientLeaveBalanceException.class);
			assertThat(balance.getUsedDays()).isEqualTo(8);
		}
	}

	@Nested
	class Reject {

		@Test
		void rejectingStoresReasonAndDoesNotAffectBalance() {
			LeaveRequest leave = pendingRequest();
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));
			when(employeeService.getEmployee(1L)).thenReturn(manager);

			LeaveRequestResponse response = service.reject(1L, 100L, "  Release week  ");

			assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
			assertThat(response.rejectionReason()).isEqualTo("Release week");
			verifyNoInteractions(leaveBalanceRepository);
		}

		@Test
		void reasonIsRequired() {
			assertThatThrownBy(() -> service.reject(1L, 100L, " "))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("reason");
			verifyNoInteractions(leaveRequestRepository);
		}

		@Test
		void onlyTheEmployeesManagerCanReject() {
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(pendingRequest()));

			assertThatThrownBy(() -> service.reject(99L, 100L, "No"))
					.isInstanceOf(AccessDeniedException.class);
		}
	}

	@Nested
	class Cancel {

		@Test
		void cancellingPendingRequestLeavesBalanceUntouched() {
			LeaveRequest leave = pendingRequest();
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));

			LeaveRequestResponse response = service.cancel(2L, 100L);

			assertThat(response.status()).isEqualTo(LeaveStatus.CANCELLED);
			verifyNoInteractions(leaveBalanceRepository);
		}

		@Test
		void cancellingApprovedRequestRestoresBalance() {
			LeaveRequest leave = pendingRequest();
			leave.setStatus(LeaveStatus.APPROVED);
			balance.setUsedDays(5);
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));
			stubBalance();

			LeaveRequestResponse response = service.cancel(2L, 100L);

			assertThat(response.status()).isEqualTo(LeaveStatus.CANCELLED);
			assertThat(balance.getUsedDays()).isZero();
		}

		@Test
		void cannotCancelApprovedLeaveThatHasStarted() {
			LeaveRequest leave = pendingRequest();
			leave.setStatus(LeaveStatus.APPROVED);
			leave.setStartDate(TODAY);
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));

			assertThatThrownBy(() -> service.cancel(2L, 100L))
					.isInstanceOf(InvalidLeaveStateException.class);
			assertThat(leave.getStatus()).isEqualTo(LeaveStatus.APPROVED);
		}

		@Test
		void cannotCancelRejectedRequest() {
			LeaveRequest leave = pendingRequest();
			leave.setStatus(LeaveStatus.REJECTED);
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leave));

			assertThatThrownBy(() -> service.cancel(2L, 100L))
					.isInstanceOf(InvalidLeaveStateException.class);
		}

		@Test
		void cannotCancelSomeoneElsesRequest() {
			when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(pendingRequest()));

			assertThatThrownBy(() -> service.cancel(3L, 100L))
					.isInstanceOf(AccessDeniedException.class);
		}
	}

	@Test
	void countsOnlyWeekdays() {
		assertThat(LeaveRequestService.countWorkingDays(NEXT_MONDAY, NEXT_FRIDAY)).isEqualTo(5);
		assertThat(LeaveRequestService.countWorkingDays(NEXT_MONDAY, NEXT_MONDAY.plusDays(13))).isEqualTo(10);
		assertThat(LeaveRequestService.countWorkingDays(TODAY.plusDays(1), TODAY.plusDays(2))).isZero();
	}

	// ---- helpers ----

	private void stubEmployeeAndType() {
		when(employeeService.getEmployee(2L)).thenReturn(employee);
		when(leaveTypeService.getLeaveType(10L)).thenReturn(annual);
	}

	private void stubNoOverlap() {
		when(leaveRequestRepository.existsOverlapping(anyLong(), any(), any(), anyCollection())).thenReturn(false);
	}

	private void stubBalance() {
		when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(2L, 10L, 2026))
				.thenReturn(Optional.of(balance));
	}

	private void stubPendingDays(long days) {
		when(leaveRequestRepository.sumPendingDays(eq(2L), eq(10L), any(), any())).thenReturn(days);
	}

	private static ApplyLeaveRequest apply(LocalDate start, LocalDate end) {
		return new ApplyLeaveRequest(10L, start, end, "Vacation");
	}

	private LeaveRequest pendingRequest() {
		LeaveRequest leave = new LeaveRequest();
		leave.setId(100L);
		leave.setEmployee(employee);
		leave.setLeaveType(annual);
		leave.setStartDate(NEXT_MONDAY);
		leave.setEndDate(NEXT_FRIDAY);
		leave.setDays(5);
		leave.setStatus(LeaveStatus.PENDING);
		return leave;
	}

	private static Employee employee(Long id, String name, Role role, Employee manager) {
		Employee e = new Employee();
		e.setId(id);
		e.setName(name);
		e.setEmail(name.split(" ")[0].toLowerCase() + "@company.com");
		e.setRole(role);
		e.setManager(manager);
		e.setActive(true);
		return e;
	}
}
