-- ============================================================================
-- Migration V1: criação do esquema inicial.
-- O Flyway executa os arquivos V<numero>__<descricao>.sql em ordem, uma única vez,
-- e registra o histórico na tabela flyway_schema_history.
-- Para mudar o banco no futuro, NUNCA edite este arquivo: crie um V2__...sql.
-- ============================================================================

CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL, -- hash BCrypt (nunca a senha pura)
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories
(
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(60) NOT NULL,
    color   VARCHAR(7), -- ex.: #FF5733
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Cada usuário não pode ter duas categorias com o mesmo nome
    CONSTRAINT uk_categories_user_name UNIQUE (user_id, name)
);

CREATE TABLE tasks
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    due_date    DATE,
    -- Se a categoria for apagada, a tarefa continua existindo, apenas sem categoria
    category_id BIGINT       REFERENCES categories (id) ON DELETE SET NULL,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT chk_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- Índices nas colunas mais usadas em filtros
CREATE INDEX idx_tasks_user_id ON tasks (user_id);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, status);
CREATE INDEX idx_tasks_category_id ON tasks (category_id);
