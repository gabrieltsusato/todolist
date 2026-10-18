package com.tsu.todolist.dto;

import com.tsu.todolist.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(
        @NotNull(message = "O status é obrigatório")
        TaskStatus status
) {
}
