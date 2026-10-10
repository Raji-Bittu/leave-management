package com.wexl.comapny.management.dto;

import jakarta.validation.constraints.NotNull;

public record AssignManagerRequest(@NotNull Long managerId) {
}
