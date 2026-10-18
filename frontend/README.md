# TodoList: Front-end

Telas geradas no Google Stitch (HTML + Tailwind) e **ligadas à API** com JavaScript puro, sem framework e sem etapa de build.

## Estrutura

```
frontend/
├── index.html        redireciona para o dashboard (logado) ou para o login
├── login.html        entrar / criar conta
├── dashboard.html    tela principal: resumo, filtros, lista, paginação e modais
└── js/
    ├── api.js        cliente da API (URL base, token JWT, tratamento de erros)
    └── dashboard.js  lógica do dashboard (estado, renderização, modais)
```

## Como rodar

1. Suba a API (`docker compose up --build`, ela fica em `http://localhost:8080`).
2. Sirva a pasta `frontend/` por HTTP (**não abra o HTML com duplo clique**):
   - **VS Code:** extensão *Live Server* > "Go Live"; ou
   - **Terminal**, dentro de `frontend/`: `python -m http.server 5500`; ou
   - **Docker:** adicione ao `docker-compose.yml`:
     ```yaml
       web:
         image: nginx:alpine
         container_name: todolist-web
         volumes:
           - ./frontend:/usr/share/nginx/html:ro
         ports:
           - "5500:80"
     ```
3. Acesse `http://localhost:5500`.

Se a API estiver em outro endereço, altere `BASE_URL` no topo de `js/api.js`. O CORS da API precisa permitir a origem do front (`CORS_ORIGINS`; em desenvolvimento o padrão é `*`).

> O front carrega Tailwind, fontes e ícones por CDN, então precisa de internet. Para produção, o ideal é compilar o Tailwind.

## O que cada tela faz

| Tela | Recursos |
|---|---|
| Login / Cadastro | Abas, mostrar/ocultar senha, medidor de senha, erros vindos da API |
| Dashboard | Cards de resumo (`/tasks/summary`), busca, filtros por status/prioridade/categoria, ordenação, paginação |
| Tarefas | Criar, editar, excluir, concluir pelo checkbox, trocar o status direto na linha, prazo vencido em vermelho |
| Categorias | Criar, editar, excluir, escolher cor com prévia ao vivo, filtrar pela barra lateral |
| Geral | Avisos (toasts), confirmação de exclusão, esqueleto de carregamento, estado vazio |

### Endpoints usados

`POST /auth/register` · `POST /auth/login` · `GET/POST /tasks` · `GET /tasks/summary` · `PUT/DELETE /tasks/{id}` · `PATCH /tasks/{id}/status` · `GET/POST /categories` · `PUT/DELETE /categories/{id}`

Filtros, ordenação e paginação são feitos **no servidor** (parâmetros `status`, `priority`, `categoryId`, `search`, `sortBy`, `direction`, `page`, `size`).

## Decisões técnicas (bom assunto para entrevista)

- **Proteção contra XSS:** todo texto do usuário passa por `esc()` antes de entrar no `innerHTML`; mensagens de aviso usam `textContent`.
- **Debounce na busca (350 ms):** não consulta a API a cada tecla.
- **Proteção contra respostas fora de ordem:** um contador (`loadSeq`) descarta respostas antigas quando o usuário muda o filtro rápido.
- **Delegação de eventos:** um único listener na lista trata todos os botões das linhas, inclusive os criados depois.
- **Sessão expirada:** qualquer resposta 401 limpa o token e volta ao login (`api.js`).
- **Datas sem fuso:** `yyyy-MM-dd` é convertido por texto (`split`), não por `Date`, para evitar o bug do "dia anterior".
- **Valores iguais aos da API:** `TODO`/`IN_PROGRESS`/`DONE`, `LOW`/`MEDIUM`/`HIGH`, `createdAt`/`dueDate`/`title`/`status`.

## Limitações conhecidas

- O token JWT fica no `localStorage` (simples, mas exposto a XSS). Em produção, usar cookie `HttpOnly`.
- Layout pensado para desktop (a barra lateral é fixa).
