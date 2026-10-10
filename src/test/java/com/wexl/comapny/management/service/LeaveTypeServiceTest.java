package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.LeaveTypeRequest;
import com.wexl.comapny.management.dto.LeaveTypeResponse;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.exception.DuplicateResourceException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.repository.LeaveTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveTypeServiceTest {

	@Mock
	private LeaveTypeRepository leaveTypeRepository;
	@InjectMocks
	private LeaveTypeService service;

	@Test
	void createDefaultsToActive() {
		when(leaveTypeRepository.existsByNameIgnoreCase("Annual")).thenReturn(false);
		when(leaveTypeRepository.save(any(LeaveType.class))).thenAnswer(inv -> inv.getArgument(0));

		LeaveTypeResponse response = service.create(new LeaveTypeRequest(" Annual ", "Paid leave", 20, null));

		assertThat(response.name()).isEqualTo("Annual");
		assertThat(response.defaultDays()).isEqualTo(20);
		assertThat(response.active()).isTrue();
	}

	@Test
	void createRejectsDuplicateName() {
		when(leaveTypeRepository.existsByNameIgnoreCase("Annual")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new LeaveTypeRequest("Annual", null, 20, true)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(leaveTypeRepository, never()).save(any());
	}

	@Test
	void updateRejectsNameUsedByAnotherType() {
		when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(type(1L, "Annual")));
		when(leaveTypeRepository.existsByNameIgnoreCaseAndIdNot("Sick", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L, new LeaveTypeRequest("Sick", null, 10, true)))
				.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void deactivateIsSoftDelete() {
		LeaveType type = type(1L, "Annual");
		when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(type));

		service.deactivate(1L);

		assertThat(type.isActive()).isFalse();
		verify(leaveTypeRepository, never()).delete(any());
	}

	@Test
	void unknownTypeIsNotFound() {
		when(leaveTypeRepository.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getLeaveType(9L)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static LeaveType type(Long id, String name) {
		LeaveType type = new LeaveType();
		type.setId(id);
		type.setName(name);
		type.setDefaultDays(20);
		type.setActive(true);
		return type;
	}
}
