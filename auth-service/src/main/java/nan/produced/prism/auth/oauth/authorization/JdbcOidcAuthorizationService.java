package nan.produced.prism.auth.oauth.authorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import nan.produced.prism.auth.oauth.oidc.OidcLoginState;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Oidc 认证信息 - 管理服务 - JDBC实现<br/>
 * 具体实现参考自：{@link org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService} <br/>
 *
 * oauth2_authorization表结构变更：
 * <ol>
 *     <li>添加session_id列 - 记录当前认证信息对应OP端session id</li>
 *     <li>添加login_state列 - 记录当前认证信息是否已登出</li>
 * </ol>
 */
public class JdbcOidcAuthorizationService implements OidcAuthorizationService{

    // @formatter:off

    /**
     * 添加session_id字段
     * 添加log
     */
    private static final String COLUMN_NAMES = "id, "
            + "registered_client_id, "
            + "principal_name, "
            + "session_id, "
            + "login_state, "
            + "authorization_grant_type, "
            + "authorized_scopes, "
            + "attributes, "
            + "state, "
            + "authorization_code_value, "
            + "authorization_code_issued_at, "
            + "authorization_code_expires_at,"
            + "authorization_code_metadata,"
            + "access_token_value,"
            + "access_token_issued_at,"
            + "access_token_expires_at,"
            + "access_token_metadata,"
            + "access_token_type,"
            + "access_token_scopes,"
            + "oidc_id_token_value,"
            + "oidc_id_token_issued_at,"
            + "oidc_id_token_expires_at,"
            + "oidc_id_token_metadata,"
            + "refresh_token_value,"
            + "refresh_token_issued_at,"
            + "refresh_token_expires_at,"
            + "refresh_token_metadata,"
            + "user_code_value,"
            + "user_code_issued_at,"
            + "user_code_expires_at,"
            + "user_code_metadata,"
            + "device_code_value,"
            + "device_code_issued_at,"
            + "device_code_expires_at,"
            + "device_code_metadata";
    // @formatter:on

    private static final String TABLE_NAME = "oauth2_authorization";

    private static final String PK_FILTER = "id = ?";

    private static final String UNKNOWN_TOKEN_TYPE_FILTER = "state = ? OR authorization_code_value = ? OR "
            + "access_token_value = ? OR oidc_id_token_value = ? OR refresh_token_value = ? OR user_code_value = ? OR "
            + "device_code_value = ?";

    private static final String STATE_FILTER = "state = ?";

    private static final String AUTHORIZATION_CODE_FILTER = "authorization_code_value = ?";

    private static final String ACCESS_TOKEN_FILTER = "access_token_value = ?";

    private static final String ID_TOKEN_FILTER = "oidc_id_token_value = ?";

    // 添加session_id筛选条件
    private static final String SESSION_ID_FILTER = "session_id = ?";

    private static final String REFRESH_TOKEN_FILTER = "refresh_token_value = ?";

    private static final String USER_CODE_FILTER = "user_code_value = ?";

    private static final String DEVICE_CODE_FILTER = "device_code_value = ?";

    // @formatter:off
    private static final String LOAD_AUTHORIZATION_SQL = "SELECT " + COLUMN_NAMES
            + " FROM " + TABLE_NAME
            + " WHERE ";
    // @formatter:on

    // @formatter:off
    private static final String SAVE_AUTHORIZATION_SQL = "INSERT INTO " + TABLE_NAME
            + " (" + COLUMN_NAMES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    // @formatter:on

    // @formatter:off
    private static final String UPDATE_AUTHORIZATION_SQL = "UPDATE " + TABLE_NAME
            + " SET registered_client_id = ?, principal_name = ?, session_id = ?, login_state = ?, authorization_grant_type = ?, authorized_scopes = ?, attributes = ?, state = ?,"
            + " authorization_code_value = ?, authorization_code_issued_at = ?, authorization_code_expires_at = ?, authorization_code_metadata = ?,"
            + " access_token_value = ?, access_token_issued_at = ?, access_token_expires_at = ?, access_token_metadata = ?, access_token_type = ?, access_token_scopes = ?,"
            + " oidc_id_token_value = ?, oidc_id_token_issued_at = ?, oidc_id_token_expires_at = ?, oidc_id_token_metadata = ?,"
            + " refresh_token_value = ?, refresh_token_issued_at = ?, refresh_token_expires_at = ?, refresh_token_metadata = ?,"
            + " user_code_value = ?, user_code_issued_at = ?, user_code_expires_at = ?, user_code_metadata = ?,"
            + " device_code_value = ?, device_code_issued_at = ?, device_code_expires_at = ?, device_code_metadata = ?"
            + " WHERE " + PK_FILTER;
    // @formatter:on

    private static final String REMOVE_AUTHORIZATION_SQL = "DELETE FROM " + TABLE_NAME + " WHERE " + PK_FILTER;

    private static Map<String, ColumnMetadata> columnMetadataMap;

    private final JdbcOperations jdbcOperations;

    private final LobHandler lobHandler;

    private RowMapper<OAuth2Authorization> authorizationRowMapper;

    private Function<OAuth2Authorization, List<SqlParameterValue>> authorizationParametersMapper;

    /**
     * Constructs a {@code JdbcOAuth2AuthorizationService} using the provided parameters.
     * @param jdbcOperations the JDBC operations
     * @param registeredClientRepository the registered client repository
     */
    public JdbcOidcAuthorizationService(JdbcOperations jdbcOperations,
                                        RegisteredClientRepository registeredClientRepository) {
        this(jdbcOperations, registeredClientRepository, new DefaultLobHandler());
    }

    /**
     * Constructs a {@code JdbcOAuth2AuthorizationService} using the provided parameters.
     * @param jdbcOperations the JDBC operations
     * @param registeredClientRepository the registered client repository
     * @param lobHandler the handler for large binary fields and large text fields
     */
    public JdbcOidcAuthorizationService(JdbcOperations jdbcOperations,
                                        RegisteredClientRepository registeredClientRepository, LobHandler lobHandler) {
        Assert.notNull(jdbcOperations, "jdbcOperations cannot be null");
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        Assert.notNull(lobHandler, "lobHandler cannot be null");
        this.jdbcOperations = jdbcOperations;
        this.lobHandler = lobHandler;
        OAuth2AuthorizationRowMapper authorizationRowMapper = new OAuth2AuthorizationRowMapper(
                registeredClientRepository);
        authorizationRowMapper.setLobHandler(lobHandler);
        this.authorizationRowMapper = authorizationRowMapper;
        this.authorizationParametersMapper = new OAuth2AuthorizationParametersMapper();
        initColumnMetadata(jdbcOperations);
    }


    @Override
    public OAuth2Authorization findByIdToken(String idToken) {
        Assert.hasText(idToken, "idToken cannot be empty");
        List<SqlParameterValue> parameters = new ArrayList<>();
        parameters.add(mapToSqlParameter("oidc_id_token_value", idToken));
        return findBy(ID_TOKEN_FILTER, parameters);
    }

    @Override
    public List<OAuth2Authorization> findBySessionId(String sessionId) {
        Assert.hasText(sessionId, "sessionId cannot be empty");
        List<SqlParameterValue> parameters = new ArrayList<>();
        parameters.add(new SqlParameterValue(Types.VARCHAR, sessionId));
        return findListBy(SESSION_ID_FILTER, parameters);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        OAuth2Authorization existingAuthorization = findById(authorization.getId());
        if (existingAuthorization == null) {
            insertAuthorization(authorization);
        }
        else {
            updateAuthorization(authorization);
        }
    }

    private void updateAuthorization(OAuth2Authorization authorization) {
        List<SqlParameterValue> parameters = this.authorizationParametersMapper.apply(authorization);
        SqlParameterValue id = parameters.remove(0);
        parameters.add(id);
        try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameters.toArray());
            this.jdbcOperations.update(UPDATE_AUTHORIZATION_SQL, pss);
        }
    }

    private void insertAuthorization(OAuth2Authorization authorization) {
        List<SqlParameterValue> parameters = this.authorizationParametersMapper.apply(authorization);
        try (LobCreator lobCreator = this.lobHandler.getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameters.toArray());
            this.jdbcOperations.update(SAVE_AUTHORIZATION_SQL, pss);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        SqlParameterValue[] parameters = new SqlParameterValue[] {
                new SqlParameterValue(Types.VARCHAR, authorization.getId()) };
        PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
        this.jdbcOperations.update(REMOVE_AUTHORIZATION_SQL, pss);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        List<SqlParameterValue> parameters = new ArrayList<>();
        parameters.add(new SqlParameterValue(Types.VARCHAR, id));
        return findBy(PK_FILTER, parameters);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        List<SqlParameterValue> parameters = new ArrayList<>();
        if (tokenType == null) {
            parameters.add(new SqlParameterValue(Types.VARCHAR, token));
            parameters.add(mapToSqlParameter("authorization_code_value", token));
            parameters.add(mapToSqlParameter("access_token_value", token));
            parameters.add(mapToSqlParameter("oidc_id_token_value", token));
            parameters.add(mapToSqlParameter("refresh_token_value", token));
            parameters.add(mapToSqlParameter("user_code_value", token));
            parameters.add(mapToSqlParameter("device_code_value", token));
            return findBy(UNKNOWN_TOKEN_TYPE_FILTER, parameters);
        }
        else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            parameters.add(new SqlParameterValue(Types.VARCHAR, token));
            return findBy(STATE_FILTER, parameters);
        }
        else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            parameters.add(mapToSqlParameter("authorization_code_value", token));
            return findBy(AUTHORIZATION_CODE_FILTER, parameters);
        }
        else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            parameters.add(mapToSqlParameter("access_token_value", token));
            return findBy(ACCESS_TOKEN_FILTER, parameters);
        }
        else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            parameters.add(mapToSqlParameter("oidc_id_token_value", token));
            return findBy(ID_TOKEN_FILTER, parameters);
        }
        else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            parameters.add(mapToSqlParameter("refresh_token_value", token));
            return findBy(REFRESH_TOKEN_FILTER, parameters);
        }
        else if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
            parameters.add(mapToSqlParameter("user_code_value", token));
            return findBy(USER_CODE_FILTER, parameters);
        }
        else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
            parameters.add(mapToSqlParameter("device_code_value", token));
            return findBy(DEVICE_CODE_FILTER, parameters);
        }
        return null;
    }

    private OAuth2Authorization findBy(String filter, List<SqlParameterValue> parameters) {
        try (LobCreator lobCreator = getLobHandler().getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameters.toArray());
            List<OAuth2Authorization> result = getJdbcOperations().query(LOAD_AUTHORIZATION_SQL + filter, pss,
                    getAuthorizationRowMapper());
            return !result.isEmpty() ? result.get(0) : null;
        }
    }

    private List<OAuth2Authorization> findListBy(String filter, List<SqlParameterValue> parameterValues) {
        try (LobCreator lobCreator = getLobHandler().getLobCreator()) {
            PreparedStatementSetter pss = new LobCreatorArgumentPreparedStatementSetter(lobCreator,
                    parameterValues.toArray());
            List<OAuth2Authorization> result = getJdbcOperations().query(LOAD_AUTHORIZATION_SQL + filter,
                    pss, getAuthorizationRowMapper());
            return result;
        }
    }

    /**
     * Sets the {@link RowMapper} used for mapping the current row in
     * {@code java.sql.ResultSet} to {@link OAuth2Authorization}. The default is
     * {@link JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper}.
     * @param authorizationRowMapper the {@link RowMapper} used for mapping the current
     * row in {@code ResultSet} to {@link OAuth2Authorization}
     */
    public final void setAuthorizationRowMapper(RowMapper<OAuth2Authorization> authorizationRowMapper) {
        Assert.notNull(authorizationRowMapper, "authorizationRowMapper cannot be null");
        this.authorizationRowMapper = authorizationRowMapper;
    }

    /**
     * Sets the {@code Function} used for mapping {@link OAuth2Authorization} to a
     * {@code List} of {@link SqlParameterValue}. The default is
     * {@link JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper}.
     * @param authorizationParametersMapper the {@code Function} used for mapping
     * {@link OAuth2Authorization} to a {@code List} of {@link SqlParameterValue}
     */
    public final void setAuthorizationParametersMapper(
            Function<OAuth2Authorization, List<SqlParameterValue>> authorizationParametersMapper) {
        Assert.notNull(authorizationParametersMapper, "authorizationParametersMapper cannot be null");
        this.authorizationParametersMapper = authorizationParametersMapper;
    }

    protected final JdbcOperations getJdbcOperations() {
        return this.jdbcOperations;
    }

    protected final LobHandler getLobHandler() {
        return this.lobHandler;
    }

    protected final RowMapper<OAuth2Authorization> getAuthorizationRowMapper() {
        return this.authorizationRowMapper;
    }

    protected final Function<OAuth2Authorization, List<SqlParameterValue>> getAuthorizationParametersMapper() {
        return this.authorizationParametersMapper;
    }

    private static void initColumnMetadata(JdbcOperations jdbcOperations) {
        columnMetadataMap = new HashMap<>();
        ColumnMetadata columnMetadata;

        columnMetadata = getColumnMetadata(jdbcOperations, "attributes", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "authorization_code_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "authorization_code_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "access_token_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "access_token_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "oidc_id_token_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "oidc_id_token_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "refresh_token_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "refresh_token_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "user_code_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "user_code_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "device_code_value", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
        columnMetadata = getColumnMetadata(jdbcOperations, "device_code_metadata", Types.BLOB);
        columnMetadataMap.put(columnMetadata.getColumnName(), columnMetadata);
    }

    private static ColumnMetadata getColumnMetadata(JdbcOperations jdbcOperations, String columnName,
                                                    int defaultDataType) {
        Integer dataType = jdbcOperations.execute((ConnectionCallback<Integer>) (conn) -> {
            DatabaseMetaData databaseMetaData = conn.getMetaData();
            ResultSet rs = databaseMetaData.getColumns(null, null, TABLE_NAME, columnName);
            if (rs.next()) {
                return rs.getInt("DATA_TYPE");
            }
            // NOTE: (Applies to HSQL)
            // When a database object is created with one of the CREATE statements or
            // renamed with the ALTER statement,
            // if the name is enclosed in double quotes, the exact name is used as the
            // case-normal form.
            // But if it is not enclosed in double quotes,
            // the name is converted to uppercase and this uppercase version is stored in
            // the database as the case-normal form.
            rs = databaseMetaData.getColumns(null, null, TABLE_NAME.toUpperCase(Locale.ENGLISH),
                    columnName.toUpperCase(Locale.ENGLISH));
            if (rs.next()) {
                return rs.getInt("DATA_TYPE");
            }
            return null;
        });
        return new ColumnMetadata(columnName, (dataType != null) ? dataType : defaultDataType);
    }

    private static SqlParameterValue mapToSqlParameter(String columnName, String value) {
        ColumnMetadata columnMetadata = columnMetadataMap.get(columnName);
        return (Types.BLOB == columnMetadata.getDataType() && StringUtils.hasText(value))
                ? new SqlParameterValue(Types.BLOB, value.getBytes(StandardCharsets.UTF_8))
                : new SqlParameterValue(columnMetadata.getDataType(), value);
    }

    /**
     * The default {@link RowMapper} that maps the current row in
     * {@code java.sql.ResultSet} to {@link OAuth2Authorization}.
     */
    public static class OAuth2AuthorizationRowMapper implements RowMapper<OAuth2Authorization> {

        private final RegisteredClientRepository registeredClientRepository;

        private LobHandler lobHandler = new DefaultLobHandler();

        private ObjectMapper objectMapper = new ObjectMapper();

        public OAuth2AuthorizationRowMapper(RegisteredClientRepository registeredClientRepository) {
            Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
            this.registeredClientRepository = registeredClientRepository;

            ClassLoader classLoader = JdbcOidcAuthorizationService.class.getClassLoader();
            List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
            this.objectMapper.registerModules(securityModules);
            this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        }

        @Override
        @SuppressWarnings("unchecked")
        public OAuth2Authorization mapRow(ResultSet rs, int rowNum) throws SQLException {
            String registeredClientId = rs.getString("registered_client_id");
            RegisteredClient registeredClient = this.registeredClientRepository.findById(registeredClientId);
            if (registeredClient == null) {
                throw new DataRetrievalFailureException("The RegisteredClient with id '" + registeredClientId
                        + "' was not found in the RegisteredClientRepository.");
            }

            OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient);
            String id = rs.getString("id");
            String principalName = rs.getString("principal_name");
            String authorizationGrantType = rs.getString("authorization_grant_type");
             Set<String> authorizedScopes = Collections.emptySet();
             String authorizedScopesString = rs.getString("authorized_scopes");
             if (authorizedScopesString != null) {
                 authorizedScopes = StringUtils.commaDelimitedListToSet(authorizedScopesString);
             }
             Map<String, Object> attributes = parseMap(getLobValue(rs, "attributes"));
             coerceAuthorizationRequestAttribute(attributes);

             builder.id(id)
                     .principalName(principalName)
                     .authorizationGrantType(new AuthorizationGrantType(authorizationGrantType))
                     .authorizedScopes(authorizedScopes)
                    .attributes((attrs) -> attrs.putAll(attributes));

            String state = rs.getString("state");
            if (StringUtils.hasText(state)) {
                builder.attribute(OAuth2ParameterNames.STATE, state);
            }

            Instant tokenIssuedAt;
            Instant tokenExpiresAt;
            String authorizationCodeValue = getLobValue(rs, "authorization_code_value");

            if (StringUtils.hasText(authorizationCodeValue)) {
                tokenIssuedAt = readInstant(rs, "authorization_code_issued_at");
                tokenExpiresAt = readInstant(rs, "authorization_code_expires_at");
                Map<String, Object> authorizationCodeMetadata = parseMap(
                        getLobValue(rs, "authorization_code_metadata"));

                OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(authorizationCodeValue,
                        tokenIssuedAt, tokenExpiresAt);
                builder.token(authorizationCode, (metadata) -> metadata.putAll(authorizationCodeMetadata));
            }

            String accessTokenValue = getLobValue(rs, "access_token_value");
            if (StringUtils.hasText(accessTokenValue)) {
                tokenIssuedAt = readInstant(rs, "access_token_issued_at");
                tokenExpiresAt = readInstant(rs, "access_token_expires_at");
                Map<String, Object> accessTokenMetadata = parseMap(getLobValue(rs, "access_token_metadata"));
                OAuth2AccessToken.TokenType tokenType = null;
                if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(rs.getString("access_token_type"))) {
                    tokenType = OAuth2AccessToken.TokenType.BEARER;
                }

                Set<String> scopes = Collections.emptySet();
                String accessTokenScopes = rs.getString("access_token_scopes");
                if (accessTokenScopes != null) {
                    scopes = StringUtils.commaDelimitedListToSet(accessTokenScopes);
                }
                OAuth2AccessToken accessToken = new OAuth2AccessToken(tokenType, accessTokenValue, tokenIssuedAt,
                        tokenExpiresAt, scopes);
                builder.token(accessToken, (metadata) -> metadata.putAll(accessTokenMetadata));
            }

            String oidcIdTokenValue = getLobValue(rs, "oidc_id_token_value");
            if (StringUtils.hasText(oidcIdTokenValue)) {
                tokenIssuedAt = readInstant(rs, "oidc_id_token_issued_at");
                tokenExpiresAt = readInstant(rs, "oidc_id_token_expires_at");
                Map<String, Object> oidcTokenMetadata = parseMap(getLobValue(rs, "oidc_id_token_metadata"));

                OidcIdToken oidcToken = new OidcIdToken(oidcIdTokenValue, tokenIssuedAt, tokenExpiresAt,
                        (Map<String, Object>) oidcTokenMetadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME));
                builder.token(oidcToken, (metadata) -> metadata.putAll(oidcTokenMetadata));
            }

            String refreshTokenValue = getLobValue(rs, "refresh_token_value");
            if (StringUtils.hasText(refreshTokenValue)) {
                tokenIssuedAt = readInstant(rs, "refresh_token_issued_at");
                tokenExpiresAt = null;
                tokenExpiresAt = readNullableInstant(rs, "refresh_token_expires_at");
                Map<String, Object> refreshTokenMetadata = parseMap(getLobValue(rs, "refresh_token_metadata"));

                OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(refreshTokenValue, tokenIssuedAt,
                        tokenExpiresAt);
                builder.token(refreshToken, (metadata) -> metadata.putAll(refreshTokenMetadata));
            }

            String userCodeValue = getLobValue(rs, "user_code_value");
            if (StringUtils.hasText(userCodeValue)) {
                tokenIssuedAt = readInstant(rs, "user_code_issued_at");
                tokenExpiresAt = readInstant(rs, "user_code_expires_at");
                Map<String, Object> userCodeMetadata = parseMap(getLobValue(rs, "user_code_metadata"));

                OAuth2UserCode userCode = new OAuth2UserCode(userCodeValue, tokenIssuedAt, tokenExpiresAt);
                builder.token(userCode, (metadata) -> metadata.putAll(userCodeMetadata));
            }

            String deviceCodeValue = getLobValue(rs, "device_code_value");
            if (StringUtils.hasText(deviceCodeValue)) {
                tokenIssuedAt = readInstant(rs, "device_code_issued_at");
                tokenExpiresAt = readInstant(rs, "device_code_expires_at");
                Map<String, Object> deviceCodeMetadata = parseMap(getLobValue(rs, "device_code_metadata"));

                OAuth2DeviceCode deviceCode = new OAuth2DeviceCode(deviceCodeValue, tokenIssuedAt, tokenExpiresAt);
                builder.token(deviceCode, (metadata) -> metadata.putAll(deviceCodeMetadata));
            }

            return builder.build();
        }

        private Instant readInstant(ResultSet rs, String column) throws SQLException {
            Instant value = readNullableInstant(rs, column);
            if (value == null) {
                throw new SQLException("Required timestamp column is null: " + column);
            }
            return value;
        }

        private Instant readNullableInstant(ResultSet rs, String column) throws SQLException {
            try {
                OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
                return odt != null ? odt.toInstant() : null;
            } catch (Exception ignore) {
                Timestamp ts = rs.getTimestamp(column);
                return ts != null ? ts.toInstant() : null;
            }
        }

        private String getLobValue(ResultSet rs, String columnName) throws SQLException {
            String columnValue = null;
            ColumnMetadata columnMetadata = columnMetadataMap.get(columnName);
            if (Types.BLOB == columnMetadata.getDataType()) {
                byte[] columnValueBytes = this.lobHandler.getBlobAsBytes(rs, columnName);
                if (columnValueBytes != null) {
                    columnValue = new String(columnValueBytes, StandardCharsets.UTF_8);
                }
            }
            else if (Types.CLOB == columnMetadata.getDataType()) {
                columnValue = this.lobHandler.getClobAsString(rs, columnName);
            }
            else {
                columnValue = rs.getString(columnName);
            }
            return columnValue;
        }

        public final void setLobHandler(LobHandler lobHandler) {
            Assert.notNull(lobHandler, "lobHandler cannot be null");
            this.lobHandler = lobHandler;
        }

        public final void setObjectMapper(ObjectMapper objectMapper) {
            Assert.notNull(objectMapper, "objectMapper cannot be null");
            this.objectMapper = objectMapper;
        }

        protected final RegisteredClientRepository getRegisteredClientRepository() {
            return this.registeredClientRepository;
        }

        protected final LobHandler getLobHandler() {
            return this.lobHandler;
        }

        protected final ObjectMapper getObjectMapper() {
            return this.objectMapper;
        }

        private Map<String, Object> parseMap(String data) {
            try {
                return this.objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
                });
            }
            catch (Exception ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }
        }

        private void coerceAuthorizationRequestAttribute(Map<String, Object> attributes) {
            if (CollectionUtils.isEmpty(attributes)) {
                return;
            }
            Object raw = attributes.get(OAuth2AuthorizationRequest.class.getName());
            if (!(raw instanceof Map<?, ?> rawMap)) {
                return;
            }
            OAuth2AuthorizationRequest authorizationRequest = this.objectMapper.convertValue(rawMap, OAuth2AuthorizationRequest.class);
            attributes.put(OAuth2AuthorizationRequest.class.getName(), authorizationRequest);
        }

    }

    /**
     * The default {@code Function} that maps {@link OAuth2Authorization} to a
     * {@code List} of {@link SqlParameterValue}.
     */
    public static class OAuth2AuthorizationParametersMapper
            implements Function<OAuth2Authorization, List<SqlParameterValue>> {

        private ObjectMapper objectMapper = new ObjectMapper();

        public OAuth2AuthorizationParametersMapper() {
            ClassLoader classLoader = JdbcOidcAuthorizationService.class.getClassLoader();
            List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
            this.objectMapper.registerModules(securityModules);
            this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        }

        @Override
        public List<SqlParameterValue> apply(OAuth2Authorization authorization) {
            List<SqlParameterValue> parameters = new ArrayList<>();
            parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getId()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getRegisteredClientId()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getPrincipalName()));

            /* ============================= Custom start==================================*/

            // 添加 session_id
            String authAttrSessionId = authorization.getAttribute("session_id");
            if (null == authAttrSessionId) {
                String sessionId = Objects.requireNonNull(RequestContextHolder.getRequestAttributes()).getSessionId();
                parameters.add(new SqlParameterValue(Types.VARCHAR, sessionId));
                // 将sessionId添加到attribute
                authorization = OAuth2Authorization.from(authorization)
                        .attribute("session_id", sessionId)
                        .build();
            } else {
                parameters.add(new SqlParameterValue(Types.VARCHAR, authAttrSessionId));
            }

            // 设置用户登录状态
            Integer authAttrLoginState = authorization.getAttribute("login_state");
            if (null == authAttrLoginState) {
                // 添加 login_state
                parameters.add(new SqlParameterValue(Types.INTEGER, OidcLoginState.LOGIN.getCode()));
                authorization = OAuth2Authorization.from(authorization)
                        .attribute("login_state", OidcLoginState.LOGIN.getCode())
                        .build();
            } else {
                parameters.add(new SqlParameterValue(Types.INTEGER, authAttrLoginState));
            }

            /* ============================= Custom finish==================================*/

            parameters.add(new SqlParameterValue(Types.VARCHAR, authorization.getAuthorizationGrantType().getValue()));

            String authorizedScopes = null;
            if (!CollectionUtils.isEmpty(authorization.getAuthorizedScopes())) {
                authorizedScopes = StringUtils.collectionToDelimitedString(authorization.getAuthorizedScopes(), ",");
            }
            parameters.add(new SqlParameterValue(Types.VARCHAR, authorizedScopes));

            String attributes = writeMap(authorization.getAttributes());
            parameters.add(mapToSqlParameter("attributes", attributes));

            String state = null;
            String authorizationState = authorization.getAttribute(OAuth2ParameterNames.STATE);
            if (StringUtils.hasText(authorizationState)) {
                state = authorizationState;
            }
            parameters.add(new SqlParameterValue(Types.VARCHAR, state));

            OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization
                    .getToken(OAuth2AuthorizationCode.class);
            List<SqlParameterValue> authorizationCodeSqlParameters = toSqlParameterList("authorization_code_value",
                    "authorization_code_metadata", authorizationCode);
            parameters.addAll(authorizationCodeSqlParameters);

            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
            List<SqlParameterValue> accessTokenSqlParameters = toSqlParameterList("access_token_value",
                    "access_token_metadata", accessToken);
            parameters.addAll(accessTokenSqlParameters);
            String accessTokenType = null;
            String accessTokenScopes = null;
            if (accessToken != null) {
                accessTokenType = accessToken.getToken().getTokenType().getValue();
                if (!CollectionUtils.isEmpty(accessToken.getToken().getScopes())) {
                    accessTokenScopes = StringUtils.collectionToDelimitedString(accessToken.getToken().getScopes(),
                            ",");
                }
            }
            parameters.add(new SqlParameterValue(Types.VARCHAR, accessTokenType));
            parameters.add(new SqlParameterValue(Types.VARCHAR, accessTokenScopes));

            OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
            List<SqlParameterValue> oidcIdTokenSqlParameters = toSqlParameterList("oidc_id_token_value",
                    "oidc_id_token_metadata", oidcIdToken);
            parameters.addAll(oidcIdTokenSqlParameters);

            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
            List<SqlParameterValue> refreshTokenSqlParameters = toSqlParameterList("refresh_token_value",
                    "refresh_token_metadata", refreshToken);
            parameters.addAll(refreshTokenSqlParameters);

            OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
            List<SqlParameterValue> userCodeSqlParameters = toSqlParameterList("user_code_value", "user_code_metadata",
                    userCode);
            parameters.addAll(userCodeSqlParameters);

            OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
            List<SqlParameterValue> deviceCodeSqlParameters = toSqlParameterList("device_code_value",
                    "device_code_metadata", deviceCode);
            parameters.addAll(deviceCodeSqlParameters);

            return parameters;
        }

        public final void setObjectMapper(ObjectMapper objectMapper) {
            Assert.notNull(objectMapper, "objectMapper cannot be null");
            this.objectMapper = objectMapper;
        }

        protected final ObjectMapper getObjectMapper() {
            return this.objectMapper;
        }

        private <T extends OAuth2Token> List<SqlParameterValue> toSqlParameterList(String tokenColumnName,
                                                                                   String tokenMetadataColumnName, OAuth2Authorization.Token<T> token) {

            List<SqlParameterValue> parameters = new ArrayList<>();
            String tokenValue = null;
            Timestamp tokenIssuedAt = null;
            Timestamp tokenExpiresAt = null;
            String metadata = null;
            if (token != null) {
                tokenValue = token.getToken().getTokenValue();
                if (token.getToken().getIssuedAt() != null) {
                    tokenIssuedAt = Timestamp.from(token.getToken().getIssuedAt());
                }
                if (token.getToken().getExpiresAt() != null) {
                    tokenExpiresAt = Timestamp.from(token.getToken().getExpiresAt());
                }
                metadata = writeMap(token.getMetadata());
            }

            parameters.add(mapToSqlParameter(tokenColumnName, tokenValue));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, tokenIssuedAt));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, tokenExpiresAt));
            parameters.add(mapToSqlParameter(tokenMetadataColumnName, metadata));
            return parameters;
        }

        private String writeMap(Map<String, Object> data) {
            try {
                // 递归转换所有不可变集合为可变集合，避免 ImmutableCollections$ListN 序列化问题
                Map<String, Object> mutableData = convertToMutableCollections(data);
                return this.objectMapper.writeValueAsString(mutableData);
            }
            catch (Exception ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }
        }

        /**
         * 递归转换 Map 中所有不可变集合为可变集合
         * 解决 Java 9+ ImmutableCollections 不在 Spring Security Jackson 白名单的问题
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> convertToMutableCollections(Map<String, Object> map) {
            if (map == null) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                result.put(entry.getKey(), convertValue(entry.getValue()));
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private Object convertValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Map) {
                Map<Object, Object> mapValue = (Map<Object, Object>) value;
                Map<Object, Object> result = new LinkedHashMap<>();
                for (Map.Entry<Object, Object> entry : mapValue.entrySet()) {
                    result.put(entry.getKey(), convertValue(entry.getValue()));
                }
                return result;
            }
            if (value instanceof List) {
                List<Object> listValue = (List<Object>) value;
                List<Object> result = new ArrayList<>(listValue.size());
                for (Object item : listValue) {
                    result.add(convertValue(item));
                }
                return result;
            }
            if (value instanceof Set) {
                Set<Object> setValue = (Set<Object>) value;
                Set<Object> result = new LinkedHashSet<>();
                for (Object item : setValue) {
                    result.add(convertValue(item));
                }
                return result;
            }
            // 对于其他类型（包括 OAuth2AuthorizationRequest 等），直接返回
            return value;
        }

    }

    private static final class LobCreatorArgumentPreparedStatementSetter extends ArgumentPreparedStatementSetter {

        private final LobCreator lobCreator;

        private LobCreatorArgumentPreparedStatementSetter(LobCreator lobCreator, Object[] args) {
            super(args);
            this.lobCreator = lobCreator;
        }

        @Override
        protected void doSetValue(PreparedStatement ps, int parameterPosition, Object argValue) throws SQLException {
            if (argValue instanceof SqlParameterValue) {
                SqlParameterValue paramValue = (SqlParameterValue) argValue;
                if (paramValue.getSqlType() == Types.BLOB) {
                    if (paramValue.getValue() != null) {
                        Assert.isInstanceOf(byte[].class, paramValue.getValue(),
                                "Value of blob parameter must be byte[]");
                    }
                    byte[] valueBytes = (byte[]) paramValue.getValue();
                    this.lobCreator.setBlobAsBytes(ps, parameterPosition, valueBytes);
                    return;
                }
                if (paramValue.getSqlType() == Types.CLOB) {
                    if (paramValue.getValue() != null) {
                        Assert.isInstanceOf(String.class, paramValue.getValue(),
                                "Value of clob parameter must be String");
                    }
                    String valueString = (String) paramValue.getValue();
                    this.lobCreator.setClobAsString(ps, parameterPosition, valueString);
                    return;
                }
            }
            super.doSetValue(ps, parameterPosition, argValue);
        }

    }

    private static final class ColumnMetadata {

        private final String columnName;

        private final int dataType;

        private ColumnMetadata(String columnName, int dataType) {
            this.columnName = columnName;
            this.dataType = dataType;
        }

        private String getColumnName() {
            return this.columnName;
        }

        private int getDataType() {
            return this.dataType;
        }

    }
}
