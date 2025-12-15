package nan.produced.prism.core.media.application.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "media_asset", indexes = {
        @Index(name = "idx_media_asset_user_folder", columnList = "userId, folderId"),
        @Index(name = "idx_media_asset_group", columnList = "groupId")
})
public class MediaAssetEntity {

    /**
     * UUID
     */
    @Id
    private String id;

    /**
     * 所属用户
     */
    @Column(nullable = false)
    private UUID userId;

    /**
     * 显示名称
     */
    @Column(nullable = false)
    private String title;

    /**
     * 用户描述
     */
    @Column(length = 128)
    private String description;

    /**
     * 所属文件夹（null = 根目录）
     */
    private String folderId;

    /**
     * 素材组Id（幂等键，配对 original + cover）
     */
    @Column(nullable = false)
    private String groupId;

    /**
     * 原始文件
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_file_id", nullable = false)
    private FileEntity originalFile;

    /**
     * 封面文件
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_file_id")
    private FileEntity coverFile;

    /**
     * 素材来源类型 1:上传 2:秒传 3:转码
     */
    @Column(nullable = false)
    private Integer sourceType;

    /**
     * 来源任务Id(若为转码)
     */
    private String sourceTaskId;

    /**
     * 元数据
     */
    @Column(columnDefinition = "jsonb")
    private String metadata;



    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

}
