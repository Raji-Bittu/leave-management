package com.wexl.comapny.management.exception;

/** A request that is well-formed but violates a business rule (mapped to HTTP 400). */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}
}
