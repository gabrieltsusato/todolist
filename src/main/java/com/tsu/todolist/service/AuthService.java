package com.tsu.todolist.service;

import com.tsu.todolist.dto.AuthResponse;
import com.tsu.todolist.dto.LoginRequest;
import com.tsu.todolist.dto.RegisterRequest;
import com.tsu.todolist.entity.User;
import com.tsu.todolist.exception.ConflictException;
import com.tsu.todolist.repository.UserRepository;
import com.tsu.todolist.security.JwtService;
import com.tsu.todolist.security.UserPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request){
        String email = normalizeEmail(request.email());

        if(userRepository.existsByEmail(email)){
            throw new ConflictException("E-mail já cadastrado");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getEmail());
        return AuthResponse.of(token, saved.getName(), saved.getEmail());
    }

    public AuthResponse login(LoginRequest request){
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal.getEmail());
        return AuthResponse.of(token, principal.getName(), principal.getEmail());
    }


    private String normalizeEmail(String email){
        return email.trim().toLowerCase();
    }
}
