-- 【必须步骤】开启 citext 扩展（只需执行一次）
CREATE EXTENSION IF NOT EXISTS citext;
-- 1. auth_users：Spring Security 主体（对应 AbstractAuthUserEntity）
DROP TABLE IF EXISTS auth_users;
CREATE TABLE auth_users (
                            id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            public_id           VARCHAR(36)  NOT NULL UNIQUE,
                            email               CITEXT       NOT NULL UNIQUE,
                            phone               CITEXT UNIQUE,
                            display_name        VARCHAR(100),
                            password_hash       VARCHAR(120) NOT NULL,
                            password_algo       VARCHAR(16)  NOT NULL DEFAULT 'bcrypt',
                            user_type           VARCHAR(16)  NOT NULL,
                            status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
                            metadata            JSONB        NOT NULL DEFAULT '{}'::jsonb,
                            created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. auth_login_alias：邮箱 / 手机等登录别名
DROP TABLE IF EXISTS auth_login_alias;
CREATE TABLE auth_login_alias (
                                  id           BIGSERIAL PRIMARY KEY,
                                  user_id      UUID        NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
                                  alias_type   VARCHAR(16) NOT NULL,
                                  alias_value  CITEXT      NOT NULL,
                                  CONSTRAINT uq_alias UNIQUE (alias_type, alias_value)
);

CREATE INDEX idx_alias_value ON auth_login_alias (alias_value);
