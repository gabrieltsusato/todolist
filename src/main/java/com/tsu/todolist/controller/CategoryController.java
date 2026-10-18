package com.tsu.todolist.controller;


import com.tsu.todolist.dto.CategoryRequest;
import com.tsu.todolist.dto.CategoryResponse;
import com.tsu.todolist.security.UserPrincipal;
import com.tsu.todolist.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categorias")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lista as categorias do usuário logado")
    public List<CategoryResponse> list(@AuthenticationPrincipal UserPrincipal user){
        return categoryService.list(user.getId());
    }

    @PostMapping
    @Operation(summary = "Cria uma categoria")
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                                   @Valid @RequestBody CategoryRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma categoria")
    public CategoryResponse update (@AuthenticationPrincipal UserPrincipal user,
                                    @PathVariable Long id,
                                    @Valid @RequestBody CategoryRequest request){
        return categoryService.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove uma categoria (as taregas dela ficam sem categoria)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id){
        categoryService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }


}
