package com.wexl.comapny.management;

import com.wexl.comapny.management.dto.ApplyLeaveRequest;
import com.wexl.comapny.management.dto.ConfigureLeaveBalanceRequest;
import com.wexl.comapny.management.dto.CreateEmployeeRequest;
import com.wexl.comapny.management.dto.LeaveTypeRequest;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.exception.OverlappingLeaveException;
import com.wexl.comapny.management.service.EmployeeService;
import com.wexl.comapny.management.service.LeaveBalanceService;
import com.wexl.comapny.management.service.LeaveRequestService;
import com.wexl.comapny.management.service.LeaveTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A double-clicked "apply" sends two identical requests at once; exactly one may be saved,
 * otherwise the same days are deducted twice when both are approved.
 */
@SpringBootTest
class ConcurrentApplyIntegrationTest {

	/** Pins "today" to Friday 9 October 2026. */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
		}
	}

	private static final int ROUNDS = 10;

	@Autowired
	private EmployeeService employeeService;
	@Autowired
	private LeaveTypeService leaveTypeService;
	@Autowired
	private LeaveBalanceService leaveBalanceService;
	@Autowired
	private LeaveRequestService leaveRequestService;

	@Test
	void simultaneousIdenticalSubmissionsSaveOnlyOneRequest() throws Exception {
		long typeId = leaveTypeService.create(new LeaveTypeRequest("Concurrent", null, 100, true)).id();
		long managerId = employeeService.create(new CreateEmployeeRequest(
				"Cora Manager", "cora@company.com", "secret1", Role.MANAGER, null)).id();
		long employeeId = employeeService.create(new CreateEmployeeRequest(
				"Dan Employee", "dan@company.com", "secret1", Role.EMPLOYEE, managerId)).id();
		leaveBalanceService.configure(employeeId, new ConfigureLeaveBalanceRequest(typeId, 2026, 100));

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// Repeat on a fresh week each round so a lucky interleaving can't hide the race.
			for (int round = 0; round < ROUNDS; round++) {
				LocalDate monday = LocalDate.of(2026, 10, 12).plusWeeks(round);
				ApplyLeaveRequest request = new ApplyLeaveRequest(typeId, monday, monday.plusDays(4), null);
				CountDownLatch start = new CountDownLatch(1);
				Callable<Boolean> submit = () -> {
					start.await();
					try {
						leaveRequestService.apply(employeeId, request);
						return true;
					} catch (OverlappingLeaveException e) {
						return false;
					}
				};
				List<Future<Boolean>> results = List.of(pool.submit(submit), pool.submit(submit));
				start.countDown();

				int saved = 0;
				for (Future<Boolean> result : results) {
					if (result.get()) {
						saved++;
					}
				}
				assertThat(saved).as("requests saved for week of %s", monday).isEqualTo(1);
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(leaveRequestService.findMyRequests(employeeId)).hasSize(ROUNDS);
	}
}
