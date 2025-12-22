package nan.produced.prism.core.program.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目期望下发关系复合主键：(programId, deviceId)。
 *
 * <p>用于 pc_program_assignment（与 pc_program_deployment 区分：assignment=期望，deployment=设备上报事实）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgramAssignmentId implements Serializable {

    private UUID programId;

    private Long deviceId;
}

