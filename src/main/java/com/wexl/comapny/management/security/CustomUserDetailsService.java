package com.wexl.comapny.management.security;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Authenticates employees by email; the role becomes the {@code ROLE_*} authority. */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final EmployeeRepository employeeRepository;

	@Override
	public UserDetails loadUserByUsername(String username) {
		Employee employee = employeeRepository.findByEmailIgnoreCase(username.trim())
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + username));
		return User.withUsername(employee.getEmail())
				.password(employee.getPassword())
				.roles(employee.getRole().name())
				.disabled(!employee.isActive())
				.build();
	}
}
