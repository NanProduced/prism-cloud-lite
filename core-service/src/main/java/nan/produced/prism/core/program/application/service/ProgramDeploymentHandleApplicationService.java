package nan.produced.prism.core.program.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.program.application.port.inbound.ProgramDeploymentHandleFacade;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramDeploymentHandleApplicationService implements ProgramDeploymentHandleFacade {

    @Override
    public void handleProgramDeploymentConsistency(Long deviceId, List<String> programVsnList) {

    }

    @Override
    public void clearProgramDeployment(Long deviceId) {

    }
}
