package com.tsu.todolist.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração: sobe a aplicação inteira (Spring + Security + JPA + H2 em memória)
 * e dispara requisições HTTP simuladas com MockMvc. Valida o fluxo real de ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoListApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------- helpers

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@email.com";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Cadastra um usuário e devolve o header Authorization pronto para uso. */
    private String registerAndGetAuthHeader(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Teste","email":"%s","password":"senha12345"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + body(result).get("token").asText();
    }

    private long createCategory(String auth, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","color":"#FF5733"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    private long createTask(String auth, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asLong();
    }

    // ---------------------------------------------------------------- testes

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        registerAndGetAuthHeader(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro","email":"%s","password":"senha12345"}""".formatted(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_withInvalidData_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"nao-e-email","password":"123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void login_withCorrectAndWrongPassword() throws Exception {
        String email = uniqueEmail();
        registerAndGetAuthHeader(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha12345"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-errada"}""".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTask_withBlankTitle_returns400() throws Exception {
        String auth = registerAndGetAuthHeader(uniqueEmail());

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void fullTodoList_createFilterUpdateStatusAndDelete() throws Exception {
        String auth = registerAndGetAuthHeader(uniqueEmail());
        long categoryId = createCategory(auth, "Faculdade");

        long taskId = createTask(auth, """
                {"title":"Entregar projeto","description":"API em Spring","priority":"HIGH",
                 "dueDate":"2030-12-31","categoryId":%d}""".formatted(categoryId));
        createTask(auth, """
                {"title":"Ler documentação","priority":"LOW"}""");

        // Listagem paginada: 2 tarefas no total
        mockMvc.perform(get("/api/tasks").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Filtro por prioridade
        mockMvc.perform(get("/api/tasks").param("priority", "HIGH").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Entregar projeto"))
                .andExpect(jsonPath("$.content[0].category.name").value("Faculdade"));

        // Filtro por categoria
        mockMvc.perform(get("/api/tasks").param("categoryId", String.valueOf(categoryId))
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Busca textual (título ou descrição)
        mockMvc.perform(get("/api/tasks").param("search", "documenta").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Paginação: 1 item por página => 2 páginas
        mockMvc.perform(get("/api/tasks").param("size", "1").header("Authorization", auth))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        // Muda o status para DONE
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        // Resumo por status
        mockMvc.perform(get("/api/tasks/summary").header("Authorization", auth))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.todo").value(1))
                .andExpect(jsonPath("$.total").value(2));

        // Remove e confirma que sumiu
        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void user_cannotAccessAnotherUsersTask() throws Exception {
        String authA = registerAndGetAuthHeader(uniqueEmail());
        String authB = registerAndGetAuthHeader(uniqueEmail());
        long taskOfA = createTask(authA, """
                {"title":"Tarefa privada da A"}""");

        // B tenta ler, alterar e apagar a tarefa de A: tudo deve dar 404
        mockMvc.perform(get("/api/tasks/" + taskOfA).header("Authorization", authB))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/tasks/" + taskOfA)
                        .header("Authorization", authB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hackeada"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/" + taskOfA).header("Authorization", authB))
                .andExpect(status().isNotFound());

        // E a lista de B não mostra nada de A
        mockMvc.perform(get("/api/tasks").header("Authorization", authB))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_withInvalidSortField_returns400() throws Exception {
        String auth = registerAndGetAuthHeader(uniqueEmail());

        mockMvc.perform(get("/api/tasks").param("sortBy", "password").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }
}

