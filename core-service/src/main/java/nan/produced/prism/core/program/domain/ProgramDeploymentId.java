package nan.produced.prism.core.program.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目发布关系复合主键：(programId, deviceId)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgramDeploymentId implements Serializable {

    private UUID programId;

    private Long deviceId;
}

