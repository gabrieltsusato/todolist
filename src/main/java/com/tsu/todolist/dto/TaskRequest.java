package com.tsu.todolist.dto;

import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(max=150, message = "O título deve ter no máximo 150 caracteres")
        String title,

        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        TaskStatus status,
        TaskPriority priority,

        LocalDate dueDate,

        Long categoryId

) {
}
