package nan.produced.prism.core.media.application.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "file_entity", indexes = {
        @Index(name = "idx_file_entity_md5", columnList = "md5", unique = true)
})
public class FileEntity {

    /**
     * UUID
     */
    @Id
    private String fileId;

    /**
     * 全局去重（可为空，大文件跳过）
     */
    @Column(unique = true)
    private String md5;

    /**
     * s3对象路径
     */
    @Column(nullable = false)
    private String s3Key;

    /**
     * 文件大小（字节）
     */
    @Column(nullable = false)
    private Long size;

    /**
     * MIME 类型
     */
    @Column(nullable = false)
    private String mimeType;

    /**
     * 引用计数
     */
    @Column(nullable = false)
    private Integer refCount = 1;

    /**
     * 媒体宽度（图片/视频）
     */
    private Integer width;

    /**
     * 媒体高度（图片/视频）
     */
    private Integer height;

    /**
     * 媒体时长（视频）
     */
    private Long durationMs;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private Instant createdAt;
}
