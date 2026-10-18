package com.tsu.todolist.service;

import com.tsu.todolist.dto.PageResponse;
import com.tsu.todolist.dto.TaskRequest;
import com.tsu.todolist.dto.TaskResponse;
import com.tsu.todolist.dto.TaskSummaryResponse;
import com.tsu.todolist.entity.Category;
import com.tsu.todolist.entity.Task;
import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;
import com.tsu.todolist.exception.BadRequestException;
import com.tsu.todolist.exception.ResourceNotFoundException;
import com.tsu.todolist.repository.CategoryRepository;
import com.tsu.todolist.repository.TaskRepository;
import com.tsu.todolist.repository.UserRepository;
import com.tsu.todolist.specification.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "dueDate", "title", "status");
    private static final int MAX_PAGE_SIZE = 50;

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request){
        Task task = new Task();
        task.setUser(userRepository.getReferenceById(userId));
        applyRequest(task, request, userId);
        return TaskResponse.from(taskRepository.save(task));

    }

    /**
     * Lista as tarefas do usuário com filtros opcionais, ordenação e paginação.
     *
     * @param page       número da página (começa em 0)
     * @param size       itens por página (máximo 50)
     * @param sortBy     createdAt | dueDate | title | status
     * @param direction  asc | desc
     */

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId,
                                           TaskStatus status,
                                           TaskPriority priority,
                                           Long categoryId,
                                           String search,
                                           int page,
                                           int size,
                                           String sortBy,
                                           String direction){
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Page<Task> result = taskRepository.findAll(
                TaskSpecifications.withFilters(userId, status, priority, categoryId, search), pageable);
        return PageResponse.from(result.map(TaskResponse::from));
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long userId, Long taskId){
        return TaskResponse.from(findOwned(userId, taskId));
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request){
            Task task = findOwned(userId,taskId);
            applyRequest(task, request, userId);
            return TaskResponse.from(task);

    }

    @Transactional
    public TaskResponse updateStatus(Long userId, Long taskId, TaskStatus newStatus){
        Task task = findOwned(userId, taskId);
        task.setStatus(newStatus);
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long userId, Long taskId){
        taskRepository.delete(findOwned(userId, taskId));
    }

    @Transactional(readOnly = true)
    public TaskSummaryResponse summary(Long userId) {
        long todo = taskRepository.countByUserIdAndStatus(userId, TaskStatus.TODO);
        long inProgress = taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long done = taskRepository.countByUserIdAndStatus(userId, TaskStatus.DONE);
        return new TaskSummaryResponse(todo, inProgress, done, todo + inProgress + done);
    }

    private Task findOwned(Long userId, Long taskId){
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(()-> new ResourceNotFoundException("Tarefa não encontrada"));
    }

    private void applyRequest(Task task, TaskRequest request, Long userId){
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());

        if(request.status() != null){
            task.setStatus(request.status());
        }
        if(request.priority() != null){
            task.setPriority(request.priority());
        }
        if(request.categoryId() != null){
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(()->new ResourceNotFoundException("Categoria não encontrada"));
            task.setCategory(category);
        }else{
            task.setCategory(null);
        }
    }

    private Pageable buildPageable(int page, int size, String sortBy, String direction){
        if(!SORTABLE_FIELDS.contains(sortBy)){
            throw new BadRequestException("Campo de ordenação inválido. Use: " + SORTABLE_FIELDS);
        }
        Sort.Direction dir;
        try{
            dir = Sort.Direction.fromString(direction);
        }catch (IllegalArgumentException ex){
            throw new BadRequestException("Direção de ordenação inválida. Use 'asc' ou 'desc");
        }
        int safePage = Math.max(page,0);
        int safeSize = Math.min(Math.max(size,1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(dir, sortBy));
    }




}
