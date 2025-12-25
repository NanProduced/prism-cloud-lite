package nan.produced.prism.core.program.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.program.application.port.inbound.ProgramDeploymentHandleFacade;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramDeploymentHandleApplicationService implements ProgramDeploymentHandleFacade {

    @Override
    public void handlePlayingReport(Long deviceId, List<String> programVsnList) {

    }
}
