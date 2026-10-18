package com.tsu.todolist.dto;

import com.tsu.todolist.entity.Category;

public record CategoryResponse(Long id, String name, String color){

    public static CategoryResponse from(Category category){
        return new CategoryResponse(category.getId(), category.getName(), category.getColor());
    }

}
