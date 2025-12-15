package nan.produced.prism.core.media.application.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "media_folder", indexes = {
        @Index(name = "idx_media_folder_user_parent", columnList = "userId, parentId")
})
public class MediaFolderEntity {

    /**
     * UUID
     */
    @Id
    private String folderId;

    /**
     * 所属用户
     */
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 128)
    private String description;

    /**
     * 父文件夹ID(null = 根目录)
     */
    private String parentFolderId;

    /**
     * 路径 - 快速查询
     */
    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;



}
