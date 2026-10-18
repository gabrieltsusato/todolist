package com.tsu.todolist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 60, message = "O nome deve ter no máximo 60 caracteres")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "A cor deve estar no formato #RRGGBB")
        String color
) {
}
