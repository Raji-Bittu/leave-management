package com.wexl.comapny.management.repository;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.LeaveRequest;
import com.wexl.comapny.management.entity.LeaveStatus;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LeaveRequestRepositoryTest {

	private static final Set<LeaveStatus> ACTIVE = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

	@Autowired
	private TestEntityManager em;
	@Autowired
	private LeaveRequestRepository repository;

	private Employee manager;
	private Employee employee;
	private LeaveType annual;

	@BeforeEach
	void setUp() {
		manager = em.persist(employee("manager@company.com", Role.MANAGER, null));
		employee = em.persist(employee("eve@company.com", Role.EMPLOYEE, manager));
		annual = new LeaveType();
		annual.setName("Annual");
		annual.setDefaultDays(20);
		annual = em.persist(annual);
		// Existing approved leave: 12-16 Oct 2026 (5 days)
		em.persist(request(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), 5, LeaveStatus.APPROVED));
		em.flush();
	}

	@Test
	void detectsOverlapOnAnyIntersection() {
		assertThat(overlaps("2026-10-16", "2026-10-20")).as("shares last day").isTrue();
		assertThat(overlaps("2026-10-08", "2026-10-12")).as("shares first day").isTrue();
		assertThat(overlaps("2026-10-13", "2026-10-14")).as("inside").isTrue();
		assertThat(overlaps("2026-10-01", "2026-10-30")).as("surrounds").isTrue();
	}

	@Test
	void adjacentRangesDoNotOverlap() {
		assertThat(overlaps("2026-10-17", "2026-10-20")).isFalse();
		assertThat(overlaps("2026-10-05", "2026-10-11")).isFalse();
	}

	@Test
	void rejectedAndCancelledRequestsDoNotBlockNewOnes() {
		em.persist(request(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), 2, LeaveStatus.REJECTED));
		em.persist(request(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), 2, LeaveStatus.CANCELLED));
		em.flush();

		assertThat(overlaps("2026-11-02", "2026-11-10")).isFalse();
	}

	@Test
	void sumsOnlyPendingDaysInYear() {
		em.persist(request(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), 3, LeaveStatus.PENDING));
		em.persist(request(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2), 2, LeaveStatus.PENDING));
		em.persist(request(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 4), 1, LeaveStatus.PENDING));
		em.flush();

		long pending = repository.sumPendingDays(employee.getId(), annual.getId(),
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

		assertThat(pending).isEqualTo(5);
	}

	@Test
	void sumIsZeroWhenNothingPending() {
		assertThat(repository.sumPendingDays(employee.getId(), annual.getId(),
				LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isZero();
	}

	@Test
	void findsRequestsOfManagersTeamOnly() {
		Employee outsider = em.persist(employee("other@company.com", Role.EMPLOYEE, null));
		LeaveRequest foreign = request(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), 1, LeaveStatus.PENDING);
		foreign.setEmployee(outsider);
		em.persist(foreign);
		em.flush();

		assertThat(repository.findByEmployeeManagerIdOrderByStartDateDesc(manager.getId()))
				.hasSize(1)
				.allMatch(r -> r.getEmployee().getId().equals(employee.getId()));
		assertThat(repository.findByEmployeeManagerIdAndStatusOrderByStartDateDesc(manager.getId(), LeaveStatus.PENDING))
				.isEmpty();
	}

	private boolean overlaps(String start, String end) {
		return repository.existsOverlapping(employee.getId(), LocalDate.parse(start), LocalDate.parse(end), ACTIVE);
	}

	private LeaveRequest request(LocalDate start, LocalDate end, int days, LeaveStatus status) {
		LeaveRequest r = new LeaveRequest();
		r.setEmployee(employee);
		r.setLeaveType(annual);
		r.setStartDate(start);
		r.setEndDate(end);
		r.setDays(days);
		r.setStatus(status);
		return r;
	}

	private static Employee employee(String email, Role role, Employee manager) {
		Employee e = new Employee();
		e.setName(email);
		e.setEmail(email);
		e.setPassword("x");
		e.setRole(role);
		e.setManager(manager);
		return e;
	}
}
