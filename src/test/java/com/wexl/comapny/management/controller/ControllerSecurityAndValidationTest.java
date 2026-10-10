package com.wexl.comapny.management.controller;

import com.wexl.comapny.management.dto.LeaveRequestResponse;
import com.wexl.comapny.management.entity.LeaveStatus;
import com.wexl.comapny.management.exception.DuplicateResourceException;
import com.wexl.comapny.management.exception.InsufficientLeaveBalanceException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.security.CurrentUserService;
import com.wexl.comapny.management.security.SecurityConfig;
import com.wexl.comapny.management.service.EmployeeService;
import com.wexl.comapny.management.service.LeaveBalanceService;
import com.wexl.comapny.management.service.LeaveRequestService;
import com.wexl.comapny.management.service.LeaveTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, EmployeeController.class, ManagerController.class})
@Import({SecurityConfig.class, ControllerSecurityAndValidationTest.FixedClockConfig.class})
class ControllerSecurityAndValidationTest {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EmployeeService employeeService;
	@MockitoBean
	private LeaveTypeService leaveTypeService;
	@MockitoBean
	private LeaveBalanceService leaveBalanceService;
	@MockitoBean
	private LeaveRequestService leaveRequestService;
	@MockitoBean
	private CurrentUserService currentUserService;

	private static final String VALID_LEAVE = """
			{"leaveTypeId": 10, "startDate": "2026-10-12", "endDate": "2026-10-16", "reason": "Vacation"}
			""";

	// ---- authentication / authorization ----

	@Test
	void unauthenticatedRequestIsRejected() throws Exception {
		mockMvc.perform(get("/api/employees/me/leave-requests"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void employeeCannotUseAdminEndpoints() throws Exception {
		mockMvc.perform(get("/api/admin/employees"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
		verifyNoInteractions(employeeService);
	}

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void employeeCannotApproveLeave() throws Exception {
		mockMvc.perform(put("/api/manager/leave-requests/1/approve"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(leaveRequestService);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminCannotApplyForLeave() throws Exception {
		mockMvc.perform(post("/api/employees/me/leave-requests")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_LEAVE))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	void wrongManagerGetsForbidden() throws Exception {
		when(currentUserService.currentEmployeeId()).thenReturn(7L);
		when(leaveRequestService.approve(7L, 1L))
				.thenThrow(new AccessDeniedException("Only the employee's manager can approve or reject this leave request"));

		mockMvc.perform(put("/api/manager/leave-requests/1/approve"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Only the employee's manager can approve or reject this leave request"));
	}

	// ---- employee endpoints ----

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void applyReturnsCreatedRequest() throws Exception {
		when(currentUserService.currentEmployeeId()).thenReturn(2L);
		when(leaveRequestService.apply(eq(2L), any())).thenReturn(sampleResponse(LeaveStatus.PENDING));

		mockMvc.perform(post("/api/employees/me/leave-requests")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_LEAVE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.days").value(5));
	}

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void applyValidatesRequiredFields() throws Exception {
		mockMvc.perform(post("/api/employees/me/leave-requests")
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.leaveTypeId").exists())
				.andExpect(jsonPath("$.fieldErrors.startDate").exists())
				.andExpect(jsonPath("$.fieldErrors.endDate").exists());
		verifyNoInteractions(leaveRequestService);
	}

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void malformedDateIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/employees/me/leave-requests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"leaveTypeId\": 10, \"startDate\": \"12-10-2026\", \"endDate\": \"2026-10-16\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "EMPLOYEE")
	void businessRuleViolationIsBadRequest() throws Exception {
		when(currentUserService.currentEmployeeId()).thenReturn(2L);
		when(leaveRequestService.apply(eq(2L), any())).thenThrow(new InsufficientLeaveBalanceException(5, 2));

		mockMvc.perform(post("/api/employees/me/leave-requests")
						.contentType(MediaType.APPLICATION_JSON).content(VALID_LEAVE))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Insufficient leave balance: requested 5 day(s), available 2"))
				.andExpect(jsonPath("$.path").value("/api/employees/me/leave-requests"));
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	void managerCanUseSelfServiceEndpoints() throws Exception {
		when(currentUserService.currentEmployeeId()).thenReturn(1L);

		mockMvc.perform(get("/api/employees/me/leave-balances"))
				.andExpect(status().isOk());
		verify(leaveBalanceService).findBalances(1L, 2026);
	}

	// ---- manager endpoints ----

	@Test
	@WithMockUser(roles = "MANAGER")
	void rejectRequiresReason() throws Exception {
		mockMvc.perform(put("/api/manager/leave-requests/1/reject")
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.reason").exists());
		verifyNoInteractions(leaveRequestService);
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	void teamRequestsCanBeFilteredByStatus() throws Exception {
		when(currentUserService.currentEmployeeId()).thenReturn(1L);

		mockMvc.perform(get("/api/manager/leave-requests").param("status", "PENDING"))
				.andExpect(status().isOk());
		verify(leaveRequestService).findTeamRequests(1L, LeaveStatus.PENDING);
	}

	@Test
	@WithMockUser(roles = "MANAGER")
	void invalidStatusFilterIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/manager/leave-requests").param("status", "SOMETHING"))
				.andExpect(status().isBadRequest());
	}

	// ---- admin endpoints ----

	@Test
	@WithMockUser(roles = "ADMIN")
	void createEmployeeValidatesInput() throws Exception {
		mockMvc.perform(post("/api/admin/employees")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"\", \"email\": \"not-an-email\", \"password\": \"123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists())
				.andExpect(jsonPath("$.fieldErrors.role").exists());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void duplicateEmployeeIsConflict() throws Exception {
		when(employeeService.create(any())).thenThrow(new DuplicateResourceException("An employee with email e@x.com already exists"));

		mockMvc.perform(post("/api/admin/employees")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"E\", \"email\": \"e@x.com\", \"password\": \"secret1\", \"role\": \"EMPLOYEE\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void unknownEmployeeIsNotFound() throws Exception {
		when(employeeService.findById(anyLong())).thenThrow(new ResourceNotFoundException("Employee", 42L));

		mockMvc.perform(get("/api/admin/employees/42"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Employee not found with id 42"));
	}

	private static LeaveRequestResponse sampleResponse(LeaveStatus status) {
		return new LeaveRequestResponse(1L, 2L, "Eve", 10L, "Annual", LocalDate.of(2026, 10, 12),
				LocalDate.of(2026, 10, 16), 5, "Vacation", status, null, null, null, Instant.now());
	}
}
