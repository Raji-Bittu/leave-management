package com.wexl.comapny.management.exception;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Object id) {
		super(resource + " not found with id " + id);
	}
}
