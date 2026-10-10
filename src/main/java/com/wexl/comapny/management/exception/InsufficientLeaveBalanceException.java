package com.wexl.comapny.management.exception;

public class InsufficientLeaveBalanceException extends BusinessRuleException {

	public InsufficientLeaveBalanceException(int requested, int available) {
		super("Insufficient leave balance: requested " + requested + " day(s), available " + available);
	}
}
