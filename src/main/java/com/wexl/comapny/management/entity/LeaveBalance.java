package com.wexl.comapny.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_balances",
		uniqueConstraints = @UniqueConstraint(name = "uk_balance_employee_type_year",
				columnNames = {"employee_id", "leave_type_id", "balance_year"}))
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "leave_type_id", nullable = false)
	private LeaveType leaveType;

	@Column(name = "balance_year", nullable = false)
	private int year;

	@Column(nullable = false)
	private int totalDays;

	@Column(nullable = false)
	private int usedDays;

	@Version
	private Long version;

	public int getAvailableDays() {
		return totalDays - usedDays;
	}
}
