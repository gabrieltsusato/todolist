package com.tsu.todolist.repository;

import com.tsu.todolist.entity.Task;
import com.tsu.todolist.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, TaskStatus status);

}
