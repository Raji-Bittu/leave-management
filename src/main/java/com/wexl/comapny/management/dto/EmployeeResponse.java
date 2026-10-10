package com.wexl.comapny.management.dto;

import com.wexl.comapny.management.entity.Role;

public record EmployeeResponse(
		Long id,
		String name,
		String email,
		Role role,
		Long managerId,
		String managerName,
		boolean active) {
}
