package com.wexl.comapny.management.exception;

public class OverlappingLeaveException extends BusinessRuleException {

	public OverlappingLeaveException() {
		super("Leave request overlaps with an existing pending or approved leave");
	}
}
