package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.CreateEmployeeRequest;
import com.wexl.comapny.management.dto.EmployeeResponse;
import com.wexl.comapny.management.dto.UpdateEmployeeRequest;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.exception.DuplicateResourceException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.mapper.EmployeeMapper;
import com.wexl.comapny.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeService {

	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public EmployeeResponse create(CreateEmployeeRequest request) {
		String email = normalizeEmail(request.email());
		if (employeeRepository.existsByEmailIgnoreCase(email)) {
			throw new DuplicateResourceException("An employee with email " + email + " already exists");
		}
		Employee employee = new Employee();
		employee.setName(request.name().trim());
		employee.setEmail(email);
		employee.setPassword(passwordEncoder.encode(request.password()));
		employee.setRole(request.role());
		employee.setActive(true);
		if (request.managerId() != null) {
			employee.setManager(resolveManager(employee, request.managerId()));
		}
		return EmployeeMapper.toResponse(employeeRepository.save(employee));
	}

	@Transactional(readOnly = true)
	public List<EmployeeResponse> findAll() {
		return employeeRepository.findAll().stream().map(EmployeeMapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public EmployeeResponse findById(Long id) {
		return EmployeeMapper.toResponse(getEmployee(id));
	}

	@Transactional
	public EmployeeResponse update(Long id, UpdateEmployeeRequest request) {
		Employee employee = getEmployee(id);
		String email = normalizeEmail(request.email());
		if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw new DuplicateResourceException("An employee with email " + email + " already exists");
		}
		boolean losesManagerRole = employee.getRole() == Role.MANAGER && request.role() != Role.MANAGER;
		boolean deactivated = employee.isActive() && !request.active();
		if (losesManagerRole || deactivated) {
			ensureHasNoActiveReports(employee);
		}
		employee.setName(request.name().trim());
		employee.setEmail(email);
		employee.setRole(request.role());
		employee.setActive(request.active());
		if (request.password() != null && !request.password().isBlank()) {
			employee.setPassword(passwordEncoder.encode(request.password()));
		}
		return EmployeeMapper.toResponse(employee);
	}

	/** Soft delete: the employee keeps their leave history but can no longer log in. */
	@Transactional
	public void deactivate(Long id) {
		Employee employee = getEmployee(id);
		ensureHasNoActiveReports(employee);
		employee.setActive(false);
	}

	@Transactional
	public EmployeeResponse assignManager(Long employeeId, Long managerId) {
		Employee employee = getEmployee(employeeId);
		employee.setManager(resolveManager(employee, managerId));
		return EmployeeMapper.toResponse(employee);
	}

	@Transactional(readOnly = true)
	public Employee getEmployee(Long id) {
		return employeeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Employee", id));
	}

	private Employee resolveManager(Employee employee, Long managerId) {
		if (employee.getId() != null && Objects.equals(employee.getId(), managerId)) {
			throw new BusinessRuleException("An employee cannot be their own manager");
		}
		Employee manager = employeeRepository.findById(managerId)
				.orElseThrow(() -> new ResourceNotFoundException("Manager", managerId));
		if (manager.getRole() != Role.MANAGER) {
			throw new BusinessRuleException("Employee " + managerId + " does not have the MANAGER role");
		}
		if (!manager.isActive()) {
			throw new BusinessRuleException("Manager " + managerId + " is inactive");
		}
		// Walk up the chain so that we never create a reporting cycle (A -> B -> A).
		for (Employee current = manager.getManager(); current != null; current = current.getManager()) {
			if (employee.getId() != null && Objects.equals(current.getId(), employee.getId())) {
				throw new BusinessRuleException("Assigning this manager would create a reporting cycle");
			}
		}
		return manager;
	}

	private void ensureHasNoActiveReports(Employee employee) {
		if (employeeRepository.existsByManagerIdAndActiveTrue(employee.getId())) {
			throw new BusinessRuleException(
					"Employee " + employee.getId() + " still manages active employees; reassign them first");
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}
}
