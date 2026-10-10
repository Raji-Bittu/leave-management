package com.wexl.comapny.management.mapper;

import com.wexl.comapny.management.dto.EmployeeResponse;
import com.wexl.comapny.management.entity.Employee;

public final class EmployeeMapper {

	private EmployeeMapper() {
	}

	public static EmployeeResponse toResponse(Employee employee) {
		Employee manager = employee.getManager();
		return new EmployeeResponse(
				employee.getId(),
				employee.getName(),
				employee.getEmail(),
				employee.getRole(),
				manager != null ? manager.getId() : null,
				manager != null ? manager.getName() : null,
				employee.isActive());
	}
}
