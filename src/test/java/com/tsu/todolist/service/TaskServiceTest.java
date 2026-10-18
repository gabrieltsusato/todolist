package com.tsu.todolist.service;

import com.tsu.todolist.dto.TaskRequest;
import com.tsu.todolist.dto.TaskResponse;
import com.tsu.todolist.entity.Task;
import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;
import com.tsu.todolist.entity.User;
import com.tsu.todolist.exception.BadRequestException;
import com.tsu.todolist.exception.ResourceNotFoundException;
import com.tsu.todolist.repository.CategoryRepository;
import com.tsu.todolist.repository.TaskRepository;
import com.tsu.todolist.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário do service com Mockito: os repositórios são "dublês" (mocks),
 * então testamos somente a regra de negócio, sem banco de dados.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void create_withoutStatusAndPriority_usesDefaults() {
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.create(1L,
                new TaskRequest("Estudar Spring", null, null, null, null, null));

        assertEquals("Estudar Spring", response.title());
        assertEquals(TaskStatus.TODO, response.status());
        assertEquals(TaskPriority.MEDIUM, response.priority());
        assertNull(response.category());
    }

    @Test
    void create_withCategoryOfAnotherUser_throwsNotFound() {
        // A categoria 99 não pertence ao usuário 1 -> o repositório não a encontra
        when(categoryRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        TaskRequest request = new TaskRequest("Tarefa", null, null, null, null, 99L);

        assertThrows(ResourceNotFoundException.class, () -> taskService.create(1L, request));
    }

    @Test
    void getById_ofAnotherUsersTask_throwsNotFound() {
        when(taskRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getById(2L, 5L));
    }

    @Test
    void updateStatus_changesOnlyTheStatus() {
        Task task = new Task();
        task.setTitle("Tarefa");
        task.setUser(new User("Ana", "ana@email.com", "hash"));
        when(taskRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.updateStatus(1L, 1L, TaskStatus.DONE);

        assertEquals(TaskStatus.DONE, response.status());
        assertEquals("Tarefa", response.title());
    }

    @Test
    void list_withInvalidSortField_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> taskService.list(1L, null, null, null, null, 0, 10, "password", "asc"));
    }

    @Test
    void delete_removesOwnedTask() {
        Task task = new Task();
        when(taskRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(task));

        taskService.delete(1L, 3L);

        verify(taskRepository).delete(task);
    }
}
