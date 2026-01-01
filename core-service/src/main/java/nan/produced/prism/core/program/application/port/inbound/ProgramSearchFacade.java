package nan.produced.prism.core.program.application.port.inbound;

import java.util.List;
import java.util.UUID;

/**
 * 全局搜索使用：节目快速检索能力（跨模块公开 API）。
 */
public interface ProgramSearchFacade {

    List<ProgramSearchItem> searchPrograms(UUID userId, String keyword, Integer limit);
}

