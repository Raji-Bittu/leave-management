package com.wexl.comapny.management.dto;

public record LeaveTypeResponse(Long id, String name, String description, int defaultDays, boolean active) {
}
