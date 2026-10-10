package com.wexl.comapny.management.repository;

import com.wexl.comapny.management.entity.LeaveRequest;
import com.wexl.comapny.management.entity.LeaveStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	/** True if the employee has an active request whose date range intersects [start, end]. */
	@Query("""
			select count(r) > 0 from LeaveRequest r
			where r.employee.id = :employeeId
			  and r.status in :statuses
			  and r.startDate <= :end
			  and r.endDate >= :start
			""")
	boolean existsOverlapping(@Param("employeeId") Long employeeId,
							  @Param("start") LocalDate start,
							  @Param("end") LocalDate end,
							  @Param("statuses") Collection<LeaveStatus> statuses);

	/** Working days already requested but not yet decided, so they can be reserved against the balance. */
	@Query("""
			select coalesce(sum(r.days), 0) from LeaveRequest r
			where r.employee.id = :employeeId
			  and r.leaveType.id = :leaveTypeId
			  and r.status = com.wexl.comapny.management.entity.LeaveStatus.PENDING
			  and r.startDate >= :yearStart
			  and r.startDate <= :yearEnd
			""")
	long sumPendingDays(@Param("employeeId") Long employeeId,
					   @Param("leaveTypeId") Long leaveTypeId,
					   @Param("yearStart") LocalDate yearStart,
					   @Param("yearEnd") LocalDate yearEnd);

	@EntityGraph(attributePaths = {"employee", "leaveType", "decidedBy"})
	List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

	@EntityGraph(attributePaths = {"employee", "leaveType", "decidedBy"})
	List<LeaveRequest> findByEmployeeManagerIdOrderByStartDateDesc(Long managerId);

	@EntityGraph(attributePaths = {"employee", "leaveType", "decidedBy"})
	List<LeaveRequest> findByEmployeeManagerIdAndStatusOrderByStartDateDesc(Long managerId, LeaveStatus status);
}
