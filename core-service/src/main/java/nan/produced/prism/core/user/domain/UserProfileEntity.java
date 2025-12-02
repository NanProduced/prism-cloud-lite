package nan.produced.prism.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 用户业务侧基本资料实体
 * 用于存储用户的核心业务信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_user_profile")
public class UserProfileEntity {

    /**
     * 用户唯一标识符
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 公开ID，对外暴露的用户标识
     */
    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    /**
     * 用户邮箱地址
     */
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /**
     * 用户手机号码
     */
    @Column(name = "phone", length = 40)
    private String phone;

    /**
     * 用户显示名称
     */
    @Column(name = "display_name", length = 80)
    private String displayName;

    /**
     * 订阅层级（如 FREE, PREMIUM 等）
     */
    @Column(name = "subscription_tier", nullable = false, length = 32)
    private String subscriptionTier = "FREE";

    /**
     * 订阅过期时间
     */
    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    /**
     * 用户元数据，以JSON格式存储
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 用户配置信息，以JSON格式存储
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configs", columnDefinition = "jsonb")
    private Map<String, Object> configs = new HashMap<>();

    /**
     * 用户创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 用户信息最后更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}