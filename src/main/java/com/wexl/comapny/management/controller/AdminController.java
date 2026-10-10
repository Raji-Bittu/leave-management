package com.wexl.comapny.management.controller;

import com.wexl.comapny.management.dto.AssignManagerRequest;
import com.wexl.comapny.management.dto.ConfigureLeaveBalanceRequest;
import com.wexl.comapny.management.dto.CreateEmployeeRequest;
import com.wexl.comapny.management.dto.EmployeeResponse;
import com.wexl.comapny.management.dto.LeaveBalanceResponse;
import com.wexl.comapny.management.dto.LeaveTypeRequest;
import com.wexl.comapny.management.dto.LeaveTypeResponse;
import com.wexl.comapny.management.dto.UpdateEmployeeRequest;
import com.wexl.comapny.management.service.EmployeeService;
import com.wexl.comapny.management.service.LeaveBalanceService;
import com.wexl.comapny.management.service.LeaveTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

	private final EmployeeService employeeService;
	private final LeaveTypeService leaveTypeService;
	private final LeaveBalanceService leaveBalanceService;
	private final Clock clock;

	// ---- Employees ----

	@PostMapping("/employees")
	@ResponseStatus(HttpStatus.CREATED)
	public EmployeeResponse createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
		return employeeService.create(request);
	}

	@GetMapping("/employees")
	public List<EmployeeResponse> listEmployees() {
		return employeeService.findAll();
	}

	@GetMapping("/employees/{id}")
	public EmployeeResponse getEmployee(@PathVariable Long id) {
		return employeeService.findById(id);
	}

	@PutMapping("/employees/{id}")
	public EmployeeResponse updateEmployee(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
		return employeeService.update(id, request);
	}

	@DeleteMapping("/employees/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivateEmployee(@PathVariable Long id) {
		employeeService.deactivate(id);
	}

	@PutMapping("/employees/{id}/manager")
	public EmployeeResponse assignManager(@PathVariable Long id, @Valid @RequestBody AssignManagerRequest request) {
		return employeeService.assignManager(id, request.managerId());
	}

	// ---- Leave balances ----

	@PutMapping("/employees/{id}/leave-balances")
	public LeaveBalanceResponse configureBalance(@PathVariable Long id,
												 @Valid @RequestBody ConfigureLeaveBalanceRequest request) {
		return leaveBalanceService.configure(id, request);
	}

	@GetMapping("/employees/{id}/leave-balances")
	public List<LeaveBalanceResponse> getBalances(@PathVariable Long id, @RequestParam(required = false) Integer year) {
		employeeService.getEmployee(id);
		return leaveBalanceService.findBalances(id, year != null ? year : LocalDate.now(clock).getYear());
	}

	// ---- Leave types ----

	@PostMapping("/leave-types")
	@ResponseStatus(HttpStatus.CREATED)
	public LeaveTypeResponse createLeaveType(@Valid @RequestBody LeaveTypeRequest request) {
		return leaveTypeService.create(request);
	}

	@GetMapping("/leave-types")
	public List<LeaveTypeResponse> listLeaveTypes() {
		return leaveTypeService.findAll();
	}

	@GetMapping("/leave-types/{id}")
	public LeaveTypeResponse getLeaveType(@PathVariable Long id) {
		return leaveTypeService.findById(id);
	}

	@PutMapping("/leave-types/{id}")
	public LeaveTypeResponse updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
		return leaveTypeService.update(id, request);
	}

	@DeleteMapping("/leave-types/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivateLeaveType(@PathVariable Long id) {
		leaveTypeService.deactivate(id);
	}
}
