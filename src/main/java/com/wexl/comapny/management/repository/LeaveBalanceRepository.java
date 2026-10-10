package com.wexl.comapny.management.repository;

import com.wexl.comapny.management.entity.LeaveBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

	Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);

	@EntityGraph(attributePaths = "leaveType")
	List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeName(Long employeeId, int year);
}
