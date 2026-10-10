package com.wexl.comapny.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectLeaveRequest(@NotBlank @Size(max = 500) String reason) {
}
