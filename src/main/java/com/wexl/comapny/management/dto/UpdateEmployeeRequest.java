package com.wexl.comapny.management.dto;

import com.wexl.comapny.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full update of an employee; {@code password} is optional and only changed when supplied. */
public record UpdateEmployeeRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 150) String email,
		@Size(min = 6, max = 100) String password,
		@NotNull Role role,
		@NotNull Boolean active) {
}
