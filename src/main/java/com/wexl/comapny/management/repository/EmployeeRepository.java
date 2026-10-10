package com.wexl.comapny.management.repository;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	Optional<Employee> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	boolean existsByRole(Role role);

	boolean existsByRoleAndActiveTrueAndIdNot(Role role, Long id);

	boolean existsByManagerIdAndActiveTrue(Long managerId);

	/** Row-locks the employee (SELECT ... FOR UPDATE) until the surrounding transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Employee e where e.id = :id")
	Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
