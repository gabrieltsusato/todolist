package com.tsu.todolist.controller;

import com.tsu.todolist.dto.*;
import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;
import com.tsu.todolist.security.UserPrincipal;
import com.tsu.todolist.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tarefas")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Cria uma tarefa")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                               @Valid @RequestBody TaskRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(user.getId(), request));
    }

    @GetMapping
    @Operation(summary = "Lista tarefas com filtros, busca, ordenação e paginação")
    public PageResponse<TaskResponse> list(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return taskService.list(user.getId(), status, priority, categoryId, search, page, size, sortBy, direction);
    }

    @GetMapping("/summary")
    @Operation(summary = "Contagem de tarefas por status (para dashboards)")
    public TaskSummaryResponse summary(@AuthenticationPrincipal UserPrincipal user) {
        return taskService.summary(user.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma tarefa por id")
    public TaskResponse getById(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return taskService.getById(user.getId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma tarefa (substitui os dados)")
    public TaskResponse update(@AuthenticationPrincipal UserPrincipal user,
                               @PathVariable Long id,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(user.getId(), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Altera apenas o status da tarefa")
    public TaskResponse updateStatus(@AuthenticationPrincipal UserPrincipal user,
                                     @PathVariable Long id,
                                     @Valid @RequestBody TaskStatusRequest request) {
        return taskService.updateStatus(user.getId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove uma tarefa")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        taskService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }


}
