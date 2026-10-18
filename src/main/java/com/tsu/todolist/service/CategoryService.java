package com.tsu.todolist.service;

import com.tsu.todolist.dto.CategoryRequest;
import com.tsu.todolist.dto.CategoryResponse;
import com.tsu.todolist.entity.Category;
import com.tsu.todolist.entity.User;
import com.tsu.todolist.exception.ConflictException;
import com.tsu.todolist.exception.ResourceNotFoundException;
import com.tsu.todolist.repository.CategoryRepository;
import com.tsu.todolist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId){
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request){
        String name = request.name().trim();
        if(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)){
            throw new ConflictException("Você já possui uma categoria com esse nome");
        }

        User user  = userRepository.getReferenceById(userId);
        Category saved = categoryRepository.save(Category.builder()
                .name(name)
                .color(request.color())
                .user(user)
                .build());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long categoryId, CategoryRequest request){
        Category category = findOwned(userId, categoryId);
        String name = request.name().trim();
        if(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, categoryId)){
            throw new ConflictException("Você já possui uma categoria com esse nome");
        }
        category.setName(name);
        category.setColor(request.color());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId){
        categoryRepository.delete(findOwned(userId, categoryId));
    }



    private Category findOwned(Long userId, Long categoryId){
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(()-> new ResourceNotFoundException("Categoria não encontrada"));
    }










}
