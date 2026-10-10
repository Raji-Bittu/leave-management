package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.CreateEmployeeRequest;
import com.wexl.comapny.management.dto.EmployeeResponse;
import com.wexl.comapny.management.dto.UpdateEmployeeRequest;
import com.wexl.comapny.management.entity.Employee;
import com.wexl.comapny.management.entity.Role;
import com.wexl.comapny.management.exception.BusinessRuleException;
import com.wexl.comapny.management.exception.DuplicateResourceException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

	@Mock
	private EmployeeRepository employeeRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@InjectMocks
	private EmployeeService service;

	@Test
	void createEncodesPasswordAndNormalizesEmail() {
		when(employeeRepository.existsByEmailIgnoreCase("eve@company.com")).thenReturn(false);
		when(passwordEncoder.encode("secret1")).thenReturn("hashed");
		when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

		EmployeeResponse response = service.create(
				new CreateEmployeeRequest(" Eve ", " Eve@Company.com ", "secret1", Role.EMPLOYEE, null));

		ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
		verify(employeeRepository).save(saved.capture());
		assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
		assertThat(response.email()).isEqualTo("eve@company.com");
		assertThat(response.name()).isEqualTo("Eve");
		assertThat(response.active()).isTrue();
	}

	@Test
	void createRejectsDuplicateEmail() {
		when(employeeRepository.existsByEmailIgnoreCase("eve@company.com")).thenReturn(true);

		assertThatThrownBy(() -> service.create(
				new CreateEmployeeRequest("Eve", "eve@company.com", "secret1", Role.EMPLOYEE, null)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(employeeRepository, never()).save(any());
	}

	@Test
	void createWithManagerRequiresManagerRole() {
		Employee notAManager = employee(5L, Role.EMPLOYEE);
		when(employeeRepository.existsByEmailIgnoreCase("eve@company.com")).thenReturn(false);
		when(passwordEncoder.encode("secret1")).thenReturn("hashed");
		when(employeeRepository.findById(5L)).thenReturn(Optional.of(notAManager));

		assertThatThrownBy(() -> service.create(
				new CreateEmployeeRequest("Eve", "eve@company.com", "secret1", Role.EMPLOYEE, 5L)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("MANAGER");
	}

	@Test
	void assignManagerSetsManager() {
		Employee employee = employee(2L, Role.EMPLOYEE);
		Employee manager = employee(1L, Role.MANAGER);
		when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(manager));

		EmployeeResponse response = service.assignManager(2L, 1L);

		assertThat(response.managerId()).isEqualTo(1L);
		assertThat(employee.getManager()).isSameAs(manager);
	}

	@Test
	void cannotBeOwnManager() {
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L, Role.MANAGER)));

		assertThatThrownBy(() -> service.assignManager(1L, 1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("own manager");
	}

	@Test
	void cannotAssignInactiveManager() {
		Employee manager = employee(1L, Role.MANAGER);
		manager.setActive(false);
		when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee(2L, Role.EMPLOYEE)));
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(manager));

		assertThatThrownBy(() -> service.assignManager(2L, 1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("inactive");
	}

	@Test
	void preventsReportingCycles() {
		Employee seniorManager = employee(1L, Role.MANAGER);
		Employee juniorManager = employee(2L, Role.MANAGER);
		juniorManager.setManager(seniorManager);
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(seniorManager));
		when(employeeRepository.findById(2L)).thenReturn(Optional.of(juniorManager));

		assertThatThrownBy(() -> service.assignManager(1L, 2L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("cycle");
	}

	@Test
	void cannotDeactivateManagerWithActiveReports() {
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L, Role.MANAGER)));
		when(employeeRepository.existsByManagerIdAndActiveTrue(1L)).thenReturn(true);

		assertThatThrownBy(() -> service.deactivate(1L))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("reassign");
	}

	@Test
	void deactivateIsSoftDelete() {
		Employee employee = employee(2L, Role.EMPLOYEE);
		when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));
		when(employeeRepository.existsByManagerIdAndActiveTrue(2L)).thenReturn(false);

		service.deactivate(2L);

		assertThat(employee.isActive()).isFalse();
		verify(employeeRepository, never()).delete(any());
	}

	@Test
	void cannotDemoteManagerWithActiveReports() {
		when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L, Role.MANAGER)));
		when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("m@company.com", 1L)).thenReturn(false);
		when(employeeRepository.existsByManagerIdAndActiveTrue(1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L,
				new UpdateEmployeeRequest("M", "m@company.com", null, Role.EMPLOYEE, true)))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void updateKeepsPasswordWhenNotSupplied() {
		Employee employee = employee(2L, Role.EMPLOYEE);
		employee.setPassword("old-hash");
		when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));
		when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("new@company.com", 2L)).thenReturn(false);

		EmployeeResponse response = service.update(2L,
				new UpdateEmployeeRequest("New Name", "new@company.com", null, Role.EMPLOYEE, true));

		assertThat(response.name()).isEqualTo("New Name");
		assertThat(employee.getPassword()).isEqualTo("old-hash");
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void unknownEmployeeIsNotFound() {
		when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(42L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("42");
	}

	private static Employee employee(Long id, Role role) {
		Employee e = new Employee();
		e.setId(id);
		e.setName("User " + id);
		e.setEmail("user" + id + "@company.com");
		e.setRole(role);
		e.setActive(true);
		return e;
	}
}
