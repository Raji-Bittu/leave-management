package com.wexl.comapny.management.security;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the logged-in user to their employee id. */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

	private final EmployeeRepository employeeRepository;

	public Long currentEmployeeId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuthenticationCredentialsNotFoundException("Not authenticated");
		}
		return employeeRepository.findByEmailIgnoreCase(authentication.getName())
				.map(Employee::getId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
						"Authenticated user is not a known employee"));
	}
}
