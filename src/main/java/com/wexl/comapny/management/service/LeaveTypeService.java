package com.wexl.comapny.management.service;

import com.wexl.comapny.management.dto.LeaveTypeRequest;
import com.wexl.comapny.management.dto.LeaveTypeResponse;
import com.wexl.comapny.management.entity.LeaveType;
import com.wexl.comapny.management.exception.DuplicateResourceException;
import com.wexl.comapny.management.exception.ResourceNotFoundException;
import com.wexl.comapny.management.mapper.LeaveMapper;
import com.wexl.comapny.management.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

	private final LeaveTypeRepository leaveTypeRepository;

	@Transactional
	public LeaveTypeResponse create(LeaveTypeRequest request) {
		String name = request.name().trim();
		if (leaveTypeRepository.existsByNameIgnoreCase(name)) {
			throw new DuplicateResourceException("Leave type '" + name + "' already exists");
		}
		LeaveType type = new LeaveType();
		apply(type, request, name);
		return LeaveMapper.toResponse(leaveTypeRepository.save(type));
	}

	@Transactional(readOnly = true)
	public List<LeaveTypeResponse> findAll() {
		return leaveTypeRepository.findAll().stream().map(LeaveMapper::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LeaveTypeResponse findById(Long id) {
		return LeaveMapper.toResponse(getLeaveType(id));
	}

	@Transactional
	public LeaveTypeResponse update(Long id, LeaveTypeRequest request) {
		LeaveType type = getLeaveType(id);
		String name = request.name().trim();
		if (leaveTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new DuplicateResourceException("Leave type '" + name + "' already exists");
		}
		apply(type, request, name);
		return LeaveMapper.toResponse(type);
	}

	/** Soft delete: existing requests and balances keep referencing the type, but no new leave can use it. */
	@Transactional
	public void deactivate(Long id) {
		getLeaveType(id).setActive(false);
	}

	@Transactional(readOnly = true)
	public LeaveType getLeaveType(Long id) {
		return leaveTypeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Leave type", id));
	}

	private static void apply(LeaveType type, LeaveTypeRequest request, String name) {
		type.setName(name);
		type.setDescription(request.description());
		type.setDefaultDays(request.defaultDays());
		type.setActive(request.active() == null || request.active());
	}
}
