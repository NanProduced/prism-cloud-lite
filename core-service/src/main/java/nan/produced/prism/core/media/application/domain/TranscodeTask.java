package nan.produced.prism.core.media.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import nan.produced.prism.core.task.application.domain.AsyncTask;

@Entity
@DiscriminatorValue("TRANSCODE")
public class TranscodeTask extends AsyncTask {

    /**
     * 源素材Id
     */
    @Column(nullable = false)
    private String sourceAssetId;

    @Column(columnDefinition = "jsonb")
    private String transcodeMetadata;

    /**
     * 产出素材ID（成功后填充）
     */
    private String outputAssetId;



    @Override
    public boolean canPurge() {
        // 已软删除 + 源素材和产物都不存在时可清理
        // 实际判断在 Service 层实现（需要查询素材表）
        return getDeletedAt() != null;
    }
}
