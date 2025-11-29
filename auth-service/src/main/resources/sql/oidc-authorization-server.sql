
-- ============================================================================
-- ============ Spring Authorization Server - PostgreSQL 建表语句 =============
-- ============================================================================
-- 1. oauth2_authorization
-- 表用途：存储 OAuth2 授权过程中的状态信息。
-- 包括：Authorization Code、Access Token、Refresh Token、OIDC ID Token 等。
-- 每当发生一次授权或令牌颁发，这里都会记录或更新数据。
-- ============================================================================
DROP TABLE IF EXISTS oauth2_authorization;
CREATE TABLE oauth2_authorization (
                                      id varchar(100) NOT NULL,
                                      registered_client_id varchar(100) NOT NULL,
                                      principal_name varchar(200) NOT NULL,
                                      authorization_grant_type varchar(100) NOT NULL,
                                      authorized_scopes varchar(1000) DEFAULT NULL,
                                      attributes text DEFAULT NULL,
                                      state varchar(500) DEFAULT NULL,

    -- 自定义
                                      session_id varchar(100) DEFAULT NULL,
                                      login_state smallint DEFAULT NULL,

    -- Authorization Code
                                      authorization_code_value text DEFAULT NULL,
                                      authorization_code_issued_at timestamp DEFAULT NULL,
                                      authorization_code_expires_at timestamp DEFAULT NULL,
                                      authorization_code_metadata text DEFAULT NULL,

    -- Access Token
                                      access_token_value text DEFAULT NULL,
                                      access_token_issued_at timestamp DEFAULT NULL,
                                      access_token_expires_at timestamp DEFAULT NULL,
                                      access_token_metadata text DEFAULT NULL,
                                      access_token_type varchar(100) DEFAULT NULL,
                                      access_token_scopes varchar(1000) DEFAULT NULL,

    -- OIDC ID Token
                                      oidc_id_token_value text DEFAULT NULL,
                                      oidc_id_token_issued_at timestamp DEFAULT NULL,
                                      oidc_id_token_expires_at timestamp DEFAULT NULL,
                                      oidc_id_token_metadata text DEFAULT NULL,

    -- Refresh Token
                                      refresh_token_value text DEFAULT NULL,
                                      refresh_token_issued_at timestamp DEFAULT NULL,
                                      refresh_token_expires_at timestamp DEFAULT NULL,
                                      refresh_token_metadata text DEFAULT NULL,

    -- User Code (Device Flow)
                                      user_code_value text DEFAULT NULL,
                                      user_code_issued_at timestamp DEFAULT NULL,
                                      user_code_expires_at timestamp DEFAULT NULL,
                                      user_code_metadata text DEFAULT NULL,

    -- Device Code (Device Flow)
                                      device_code_value text DEFAULT NULL,
                                      device_code_issued_at timestamp DEFAULT NULL,
                                      device_code_expires_at timestamp DEFAULT NULL,
                                      device_code_metadata text DEFAULT NULL,

                                      PRIMARY KEY (id)
);

CREATE INDEX idx_oauth2_authorization_session_id ON oauth2_authorization (session_id);

-- 表注释
COMMENT ON TABLE oauth2_authorization IS 'OAuth2 授权信息表';

-- 基础字段注释
COMMENT ON COLUMN oauth2_authorization.id IS '主键ID';
COMMENT ON COLUMN oauth2_authorization.registered_client_id IS '客户端ID';
COMMENT ON COLUMN oauth2_authorization.principal_name IS '用户主体名称';
COMMENT ON COLUMN oauth2_authorization.authorization_grant_type IS '授权授予类型';
COMMENT ON COLUMN oauth2_authorization.authorized_scopes IS '已授权的Scopes';
COMMENT ON COLUMN oauth2_authorization.attributes IS '自定义属性Map';
COMMENT ON COLUMN oauth2_authorization.state IS 'OAuth2 State参数';

-- 自定义
COMMENT ON COLUMN oauth2_authorization.session_id IS '浏览器端session id';
COMMENT ON COLUMN oauth2_authorization.login_state IS '登录状态（1:已登录, 2:已登出, 3:已过期）';

-- Authorization Code 字段注释
COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS '授权码值';
COMMENT ON COLUMN oauth2_authorization.authorization_code_issued_at IS '授权码颁发时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_expires_at IS '授权码过期时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_metadata IS '授权码元数据';

-- Access Token 字段注释
COMMENT ON COLUMN oauth2_authorization.access_token_value IS '访问令牌值';
COMMENT ON COLUMN oauth2_authorization.access_token_issued_at IS '访问令牌颁发时间';
COMMENT ON COLUMN oauth2_authorization.access_token_expires_at IS '访问令牌过期时间';
COMMENT ON COLUMN oauth2_authorization.access_token_metadata IS '访问令牌元数据';
COMMENT ON COLUMN oauth2_authorization.access_token_type IS '访问令牌类型(如Bearer)';
COMMENT ON COLUMN oauth2_authorization.access_token_scopes IS '访问令牌包含的Scopes';

-- OIDC ID Token 字段注释
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS 'OIDC ID Token值';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_issued_at IS 'ID Token颁发时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_expires_at IS 'ID Token过期时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_metadata IS 'ID Token元数据';

-- Refresh Token 字段注释
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS '刷新令牌值';
COMMENT ON COLUMN oauth2_authorization.refresh_token_issued_at IS '刷新令牌颁发时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_expires_at IS '刷新令牌过期时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_metadata IS '刷新令牌元数据';

-- User Code 字段注释
COMMENT ON COLUMN oauth2_authorization.user_code_value IS '用户码值';
COMMENT ON COLUMN oauth2_authorization.user_code_issued_at IS '用户码颁发时间';
COMMENT ON COLUMN oauth2_authorization.user_code_expires_at IS '用户码过期时间';
COMMENT ON COLUMN oauth2_authorization.user_code_metadata IS '用户码元数据';

-- Device Code 字段注释
COMMENT ON COLUMN oauth2_authorization.device_code_value IS '设备码值';
COMMENT ON COLUMN oauth2_authorization.device_code_issued_at IS '设备码颁发时间';
COMMENT ON COLUMN oauth2_authorization.device_code_expires_at IS '设备码过期时间';
COMMENT ON COLUMN oauth2_authorization.device_code_metadata IS '设备码元数据';

-- ============================================================================
-- 2. oauth2_authorization_consent
-- 表用途：存储“用户”对“客户端”的授权确认信息 (Consent)。
-- 场景：当用户点击“我同意应用访问我的个人资料”时，记录产生。
-- 作用：记住用户的选择，下次登录时无需再次询问（除非撤销）。
-- ============================================================================
DROP TABLE IF EXISTS oauth2_authorization_consent;
CREATE TABLE oauth2_authorization_consent (
                                              registered_client_id varchar(100) NOT NULL,
                                              principal_name varchar(200) NOT NULL,
                                              authorities varchar(1000) NOT NULL,
                                              PRIMARY KEY (registered_client_id, principal_name)
);

COMMENT ON TABLE oauth2_authorization_consent IS '申请权限同意信息';

-- ============================================================================
-- 3. oauth2_registered_client
-- 表用途：存储客户端注册信息。
-- 作用：类似于“应用管理”表，定义了哪些 App 可以连接认证服务器，以及它们的配置。
-- ============================================================================
DROP TABLE IF EXISTS oauth2_registered_client;
CREATE TABLE oauth2_registered_client (
                                          id varchar(100) NOT NULL,
                                          client_id varchar(100) NOT NULL,
                                          client_id_issued_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          client_secret varchar(200) DEFAULT NULL,
                                          client_secret_expires_at timestamp DEFAULT NULL,
                                          client_name varchar(200) NOT NULL,
                                          client_authentication_methods varchar(1000) NOT NULL,
                                          authorization_grant_types varchar(1000) NOT NULL,
                                          redirect_uris varchar(1000) DEFAULT NULL,
                                          scopes varchar(1000) NOT NULL,
                                          post_logout_redirect_uris varchar(1000) DEFAULT NULL,
                                          client_settings varchar(2000) NOT NULL,
                                          token_settings varchar(2000) NOT NULL,
                                          PRIMARY KEY (id)
);

COMMENT ON TABLE oauth2_registered_client IS '客户端注册信息';
COMMENT ON COLUMN oauth2_registered_client.id IS '主键ID';
COMMENT ON COLUMN oauth2_registered_client.client_id IS '客户端ID';
COMMENT ON COLUMN oauth2_registered_client.client_id_issued_at IS '发布时间';
COMMENT ON COLUMN oauth2_registered_client.client_secret IS '客户端密钥';
COMMENT ON COLUMN oauth2_registered_client.client_secret_expires_at IS '客户端密钥过期时间';
COMMENT ON COLUMN oauth2_registered_client.client_name IS '客户端名称';
COMMENT ON COLUMN oauth2_registered_client.client_authentication_methods IS '客户端认证Method';
COMMENT ON COLUMN oauth2_registered_client.authorization_grant_types IS '客户端支持的授权类型';
COMMENT ON COLUMN oauth2_registered_client.redirect_uris IS '客户端认证通过后的重定向URI';
COMMENT ON COLUMN oauth2_registered_client.scopes IS '客户端权限列表';
COMMENT ON COLUMN oauth2_registered_client.post_logout_redirect_uris IS '注销成功后的重定向URI';
COMMENT ON COLUMN oauth2_registered_client.client_settings IS '客户端设置Map';
COMMENT ON COLUMN oauth2_registered_client.token_settings IS '令牌设置Map';