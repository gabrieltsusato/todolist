package com.tsu.todolist.security;

import com.tsu.todolist.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String password;

    public static UserPrincipal from (User user){
        return new UserPrincipal(user.getId(), user.getName(), user.getEmail(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Projeto simples: todo usuário tem o papel ROLE_USER. Evolua aqui para ROLE_ADMIN se quiser.
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
