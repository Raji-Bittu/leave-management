package com.wexl.comapny.management.dto;

import com.wexl.comapny.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 150) String email,
		@NotBlank @Size(min = 6, max = 100) String password,
		@NotNull Role role,
		Long managerId) {
}
