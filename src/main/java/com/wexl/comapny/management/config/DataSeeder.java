package com.wexl.comapny.management.config;

import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first admin account so the API can be used on an empty database. */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.admin.name}")
	private String adminName;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Value("${app.admin.password}")
	private String adminPassword;

	@Override
	@Transactional
	public void run(String... args) {
		if (employeeRepository.existsByRole(Role.ADMIN)) {
			return;
		}
		Employee admin = new Employee();
		admin.setName(adminName);
		admin.setEmail(adminEmail.toLowerCase());
		admin.setPassword(passwordEncoder.encode(adminPassword));
		admin.setRole(Role.ADMIN);
		admin.setActive(true);
		employeeRepository.save(admin);
		log.info("Created default admin user '{}'", adminEmail);
	}
}
