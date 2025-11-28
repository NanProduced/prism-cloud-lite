-- ================================================================
-- ===================== PostgreSQL 建表语句 =======================
-- ================================================================

-- ----------------------------
-- 1. Table structure for oauth2_registered_client
-- ----------------------------
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
COMMENT ON COLUMN oauth2_registered_client.client_settings IS '客户端设置Map';
COMMENT ON COLUMN oauth2_registered_client.token_settings IS '令牌设置Map';


-- ----------------------------
-- 2. Table structure for oauth2_authorization
-- ----------------------------
DROP TABLE IF EXISTS oauth2_authorization;
CREATE TABLE oauth2_authorization (
                                      id varchar(100) NOT NULL,
                                      registered_client_id varchar(100) NOT NULL,
                                      principal_name varchar(200) NOT NULL,
                                      session_id varchar(100) DEFAULT NULL,
                                      login_state smallint DEFAULT NULL,
                                      authorization_grant_type varchar(100) NOT NULL,
                                      attributes text DEFAULT NULL,
                                      state varchar(500) DEFAULT NULL,
                                      authorization_code_value text DEFAULT NULL,
                                      authorization_code_issued_at timestamp DEFAULT NULL,
                                      authorization_code_expires_at timestamp DEFAULT NULL,
                                      authorization_code_metadata text DEFAULT NULL,
                                      access_token_value text DEFAULT NULL,
                                      access_token_issued_at timestamp DEFAULT NULL,
                                      access_token_expires_at timestamp DEFAULT NULL,
                                      access_token_metadata text DEFAULT NULL,
                                      access_token_type varchar(100) DEFAULT NULL,
                                      access_token_scopes varchar(1000) DEFAULT NULL,
                                      oidc_id_token_value text DEFAULT NULL,
                                      oidc_id_token_issued_at timestamp DEFAULT NULL,
                                      oidc_id_token_expires_at timestamp DEFAULT NULL,
                                      oidc_id_token_metadata text DEFAULT NULL,
                                      refresh_token_value text DEFAULT NULL,
                                      refresh_token_issued_at timestamp DEFAULT NULL,
                                      refresh_token_expires_at timestamp DEFAULT NULL,
                                      refresh_token_metadata text DEFAULT NULL,
                                      PRIMARY KEY (id)
);

-- 为自定义字段添加索引，优化 BackChannelLogout 查询性能
CREATE INDEX idx_oauth2_authorization_session_id ON oauth2_authorization (session_id);

COMMENT ON TABLE oauth2_authorization IS '认证通过过的客户端、令牌、用户信息';
COMMENT ON COLUMN oauth2_authorization.id IS '主键ID';
COMMENT ON COLUMN oauth2_authorization.registered_client_id IS 'cilent注册ID';
COMMENT ON COLUMN oauth2_authorization.principal_name IS '用户名';
COMMENT ON COLUMN oauth2_authorization.session_id IS '浏览器端session id（对应EU所在浏览器）';
COMMENT ON COLUMN oauth2_authorization.login_state IS '登录状态（1:已登录, 2:已登出）';
COMMENT ON COLUMN oauth2_authorization.authorization_grant_type IS '授权类型';
COMMENT ON COLUMN oauth2_authorization.attributes IS '属性集合';
COMMENT ON COLUMN oauth2_authorization.state IS 'state';
COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS '授权码code值对象';
COMMENT ON COLUMN oauth2_authorization.authorization_code_issued_at IS '授权码code发布时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_expires_at IS '授权码code过期时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_metadata IS '授权码code元数据';
COMMENT ON COLUMN oauth2_authorization.access_token_value IS '访问令牌accessToken对象';
COMMENT ON COLUMN oauth2_authorization.access_token_issued_at IS '访问令牌accessToken发布时间';
COMMENT ON COLUMN oauth2_authorization.access_token_expires_at IS '访问令牌accessToken过期时间';
COMMENT ON COLUMN oauth2_authorization.access_token_metadata IS '访问令牌accessToken元数据';
COMMENT ON COLUMN oauth2_authorization.access_token_type IS '访问令牌accessToken类型';
COMMENT ON COLUMN oauth2_authorization.access_token_scopes IS '访问令牌accessToken scope列表';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS 'OIDC身份令牌idToken对象';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_issued_at IS 'OIDC身份令牌idToken发布时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_expires_at IS 'OIDC身份令牌idToken过期时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_metadata IS 'OIDC身份令牌idToken元数据';
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS '刷新令牌refreshToken值对象';
COMMENT ON COLUMN oauth2_authorization.refresh_token_issued_at IS '刷新令牌refreshToken发布时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_expires_at IS '刷新令牌refreshToken过期时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_metadata IS '刷新令牌refreshToken元数据';


-- ----------------------------
-- 3. Table structure for oauth2_authorization_consent
-- ----------------------------
DROP TABLE IF EXISTS oauth2_authorization_consent;
CREATE TABLE oauth2_authorization_consent (
                                              registered_client_id varchar(100) NOT NULL,
                                              principal_name varchar(200) NOT NULL,
                                              authorities varchar(1000) NOT NULL,
                                              PRIMARY KEY (registered_client_id, principal_name)
);

COMMENT ON TABLE oauth2_authorization_consent IS '申请权限同意信息';
COMMENT ON COLUMN oauth2_authorization_consent.registered_client_id IS 'client注册ID';
COMMENT ON COLUMN oauth2_authorization_consent.principal_name IS '用户名';
COMMENT ON COLUMN oauth2_authorization_consent.authorities IS '权限列表';