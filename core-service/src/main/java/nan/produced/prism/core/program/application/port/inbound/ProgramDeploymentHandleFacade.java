package nan.produced.prism.core.program.application.port.inbound;

import java.util.List;

public interface ProgramDeploymentHandleFacade {

    /**
     * 处理deployment一致性
     * <p>管理device和program的关联关系</p>
     * @param deviceId 设备ID
     * @param programVsnList 播放的program的vsn名，例如：Playlist4406-v3_343335c016f8a206c93143700db5e05b_9596.vsn
     */
    void handleProgramDeploymentConsistency(Long deviceId, List<String> programVsnList);

    /**
     * 清理program deployment关系
     * @param deviceId 设备ID
     */
    void clearProgramDeployment(Long deviceId);
}
