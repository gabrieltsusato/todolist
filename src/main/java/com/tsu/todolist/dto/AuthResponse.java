package com.tsu.todolist.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String name,
        String email
) {
    public static AuthResponse of(String token, String name, String email){
        return new AuthResponse(token, "Bearer", name, email);
    }

}
