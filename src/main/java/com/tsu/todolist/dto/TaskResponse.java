package com.tsu.todolist.dto;

import com.tsu.todolist.entity.Task;
import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        CategoryResponse category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TaskResponse from(Task task){
        CategoryResponse category = task.getCategory() != null
                ? CategoryResponse.from(task.getCategory())
                :null;
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                category,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
