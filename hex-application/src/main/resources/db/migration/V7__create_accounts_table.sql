CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_accounts_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

