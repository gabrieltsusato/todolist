package com.tsu.todolist.specification;

import com.tsu.todolist.entity.Task;
import com.tsu.todolist.entity.TaskPriority;
import com.tsu.todolist.entity.TaskStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TaskSpecifications {

    private TaskSpecifications(){
    }

    public static Specification<Task> withFilters(Long userId,
                                                  TaskStatus status,
                                                  TaskPriority priority,
                                                  Long categoryId,
                                                  String search){
        return (root, query, cb) -> {
            // Evita o problema N+1: traz a categoria no mesmo SELECT.
            // O "if" impede o fetch na query de contagem (count), que não aceita fetch join.
            Class<?> resultType = query.getResultType();
            if (!Long.class.equals(resultType) && !long.class.equals(resultType)) {
                root.fetch("category", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // SEMPRE filtra pelo dono: um usuário só enxerga as próprias tarefas
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
