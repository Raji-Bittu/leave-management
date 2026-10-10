package com.wexl.comapny.management.repository;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	Optional<Employee> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	boolean existsByRole(Role role);

	boolean existsByManagerIdAndActiveTrue(Long managerId);
}
