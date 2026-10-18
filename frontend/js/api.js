/**
 * api.js
 * ------------------------------------------------------------------
 * Cliente JavaScript da API TodoList. Todas as telas passam por aqui:
 * centraliza a URL base, o envio do token JWT e o tratamento de erros.
 *
 * Uso nas páginas:  TodoList.login(email, senha)  |  TodoList.listTasks({ status: "TODO" })
 *
 * Segurança: guardar o JWT no localStorage é simples e comum em projetos de estudo,
 * mas fica exposto a ataques XSS. Em produção, o ideal é um cookie HttpOnly.
 * (Por isso o dashboard sempre escapa o texto do usuário antes de inserir no HTML.)
 */
const TodoList = (() => {
  // Altere aqui (ou defina window.TODOLIST_API_URL antes de carregar este arquivo)
  const BASE_URL = window.TODOLIST_API_URL || "http://localhost:8080/api";
  const TOKEN_KEY = "todolist_token";
  const USER_KEY = "todolist_user";

  // ---------------------------------------------------------------- sessão
  const getToken = () => localStorage.getItem(TOKEN_KEY);
  const isLoggedIn = () => !!getToken();

  function getUser() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY)) || null;
    } catch {
      return null;
    }
  }

  function saveSession(data) {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ name: data.name, email: data.email }));
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  // ---------------------------------------------------------------- requisição base
  /**
   * Monta os cabeçalhos, envia o token e padroniza os erros.
   * Os erros da API chegam como { message, fieldErrors }; viram um Error com .status e .fieldErrors.
   */
  async function request(path, { method = "GET", body, auth = true } = {}) {
    const headers = { "Content-Type": "application/json" };
    if (auth && getToken()) headers["Authorization"] = "Bearer " + getToken();

    let response;
    try {
      response = await fetch(BASE_URL + path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      // fetch só lança erro quando não consegue falar com o servidor (API fora do ar, CORS, rede)
      throw new Error("Não foi possível conectar ao servidor. Verifique se a API está rodando.");
    }

    // Token ausente, inválido ou expirado em rota protegida: encerra a sessão e volta ao login
    if (response.status === 401 && auth) {
      logout();
      window.location.href = "login.html";
      throw new Error("Sessão expirada. Faça login novamente.");
    }

    if (response.status === 204) return null; // DELETE não devolve corpo

    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error((data && data.message) || "Erro inesperado. Tente novamente.");
      error.status = response.status;
      error.fieldErrors = (data && data.fieldErrors) || null;
      throw error;
    }
    return data;
  }

  // ---------------------------------------------------------------- API pública
  return {
    isLoggedIn,
    getUser,
    logout,

    // ----- Autenticação -----
    async register(name, email, password) {
      const data = await request("/auth/register", { method: "POST", auth: false, body: { name, email, password } });
      saveSession(data);
      return data;
    },
    async login(email, password) {
      const data = await request("/auth/login", { method: "POST", auth: false, body: { email, password } });
      saveSession(data);
      return data;
    },

    // ----- Tarefas -----
    // filtros: { status, priority, categoryId, search, page, size, sortBy, direction }
    listTasks(filters = {}) {
      const params = new URLSearchParams();
      Object.entries(filters).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== "") params.append(k, v);
      });
      const query = params.toString();
      return request("/tasks" + (query ? "?" + query : ""));
    },
    getTask: (id) => request("/tasks/" + id),
    createTask: (task) => request("/tasks", { method: "POST", body: task }),
    updateTask: (id, task) => request("/tasks/" + id, { method: "PUT", body: task }),
    updateTaskStatus: (id, status) => request("/tasks/" + id + "/status", { method: "PATCH", body: { status } }),
    deleteTask: (id) => request("/tasks/" + id, { method: "DELETE" }),
    taskSummary: () => request("/tasks/summary"),

    // ----- Categorias -----
    listCategories: () => request("/categories"),
    createCategory: (category) => request("/categories", { method: "POST", body: category }),
    updateCategory: (id, category) => request("/categories/" + id, { method: "PUT", body: category }),
    deleteCategory: (id) => request("/categories/" + id, { method: "DELETE" }),
  };
})();
