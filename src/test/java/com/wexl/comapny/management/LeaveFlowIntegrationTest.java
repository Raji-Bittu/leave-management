package com.wexl.comapny.management;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow through the real security filter chain, services and an H2 database:
 * admin setup, apply, approve, cancel, and the main rule violations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeaveFlowIntegrationTest {

	/** Pins "today" to Friday 9 October 2026. */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void fullLeaveLifecycle() throws Exception {
		// --- Admin setup ---
		long typeId = createdId(asAdmin(post("/api/admin/leave-types"))
				.content("{\"name\": \"Annual\", \"description\": \"Paid\", \"defaultDays\": 20}"));
		long managerId = createdId(asAdmin(post("/api/admin/employees")).content("""
				{"name": "Mona Manager", "email": "mona@company.com", "password": "secret1", "role": "MANAGER"}"""));
		long otherManagerId = createdId(asAdmin(post("/api/admin/employees")).content("""
				{"name": "Oscar Other", "email": "oscar@company.com", "password": "secret1", "role": "MANAGER"}"""));
		long employeeId = createdId(asAdmin(post("/api/admin/employees")).content("""
				{"name": "Eve Employee", "email": "eve@company.com", "password": "secret1", "role": "EMPLOYEE"}"""));

		mockMvc.perform(asAdmin(put("/api/admin/employees/" + employeeId + "/manager"))
						.content("{\"managerId\": " + managerId + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.managerId").value(managerId));
		mockMvc.perform(asAdmin(put("/api/admin/employees/" + employeeId + "/leave-balances"))
						.content("{\"leaveTypeId\": " + typeId + ", \"year\": 2026, \"totalDays\": 10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableDays").value(10));

		// --- Rule violations on apply ---
		mockMvc.perform(as("eve", post("/api/employees/me/leave-requests")).content(leave(typeId, "2026-10-01", "2026-10-02")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Leave cannot be applied for past dates"));
		mockMvc.perform(as("eve", post("/api/employees/me/leave-requests")).content(leave(typeId, "2026-10-12", "2026-10-30")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Insufficient leave balance: requested 15 day(s), available 10"));

		// --- Apply: pending, balance unchanged ---
		long requestId = createdId(as("eve", post("/api/employees/me/leave-requests"))
				.content(leave(typeId, "2026-10-12", "2026-10-16")));
		expectAvailable(10);

		mockMvc.perform(as("eve", post("/api/employees/me/leave-requests")).content(leave(typeId, "2026-10-14", "2026-10-20")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Leave request overlaps with an existing pending or approved leave"));

		// --- Only Eve's manager can decide ---
		mockMvc.perform(as("oscar", put("/api/manager/leave-requests/" + requestId + "/approve")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as("eve", put("/api/manager/leave-requests/" + requestId + "/approve")))
				.andExpect(status().isForbidden());
		mockMvc.perform(as("oscar", get("/api/manager/leave-requests")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(as("mona", get("/api/manager/leave-requests")).param("status", "PENDING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// --- Approve: balance reduced ---
		mockMvc.perform(as("mona", put("/api/manager/leave-requests/" + requestId + "/approve")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedById").value(managerId));
		expectAvailable(5);
		mockMvc.perform(as("mona", put("/api/manager/leave-requests/" + requestId + "/approve")))
				.andExpect(status().isBadRequest());

		// --- Cancel approved: balance restored ---
		mockMvc.perform(as("eve", put("/api/employees/me/leave-requests/" + requestId + "/cancel")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		expectAvailable(10);

		// --- Reject: reason required, balance unaffected ---
		long secondId = createdId(as("eve", post("/api/employees/me/leave-requests"))
				.content(leave(typeId, "2026-11-02", "2026-11-03")));
		mockMvc.perform(as("mona", put("/api/manager/leave-requests/" + secondId + "/reject")).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(as("mona", put("/api/manager/leave-requests/" + secondId + "/reject"))
						.content("{\"reason\": \"Release week\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").value("Release week"));
		expectAvailable(10);

		mockMvc.perform(as("eve", get("/api/employees/me/leave-requests")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		// --- Wrong password is unauthorized ---
		mockMvc.perform(get("/api/employees/me/leave-requests").with(httpBasic("eve@company.com", "wrong")))
				.andExpect(status().isUnauthorized());
		// otherManagerId exists only to act as the "wrong manager"
		mockMvc.perform(asAdmin(get("/api/admin/employees/" + otherManagerId)))
				.andExpect(status().isOk());
	}

	private void expectAvailable(int days) throws Exception {
		mockMvc.perform(as("eve", get("/api/employees/me/leave-balances")).param("year", "2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].availableDays").value(days));
	}

	private long createdId(MockHttpServletRequestBuilder request) throws Exception {
		String body = mockMvc.perform(request)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.with(httpBasic("admin@company.com", "admin123")).contentType(MediaType.APPLICATION_JSON);
	}

	private static MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) {
		return request.with(httpBasic(user + "@company.com", "secret1")).contentType(MediaType.APPLICATION_JSON);
	}

	private static String leave(long typeId, String start, String end) {
		return "{\"leaveTypeId\": " + typeId + ", \"startDate\": \"" + start + "\", \"endDate\": \"" + end + "\"}";
	}
}
