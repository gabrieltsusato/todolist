package com.tsu.todolist.config;

import com.tsu.todolist.security.JwtAuthenticationFilter;
import com.tsu.todolist.security.JwtService;
import com.tsu.todolist.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint entryPoint) {
        http
                // CSRF protege aplicações com sessão/cookies. Como usamos JWT no cabeçalho
                // (stateless), o ataque CSRF não se aplica e podemos desligar.
                .csrf(AbstractHttpConfigurer::disable)

                // Usa o bean corsConfigurationSource() definido abaixo
                .cors(Customizer.withDefaults())

                // STATELESS: o servidor não guarda sessão. Cada requisição se autentica pelo token.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))

                .authorizeHttpRequests(auth -> auth
                        // Rotas públicas: cadastro/login e documentação Swagger
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Preflight do CORS (requisição OPTIONS que o navegador envia antes)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Todo o resto exige autenticação
                        .anyRequest().authenticated())

                // Nosso filtro JWT roda ANTES do filtro padrão de usuário/senha
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt: hash de senha com "salt" aleatório embutido e custo ajustável. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Exposto como bean para o AuthService validar e-mail/senha no login. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }

    /**
     * CORS: permite que o front (que roda em outra origem/porta, como as páginas geradas pelo Stitch)
     * chame esta API pelo navegador. Em produção, liste apenas o domínio real do front em CORS_ORIGINS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


}
