package nan.produced.prism.core.program.application.listener;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.api.event.DeviceInternetProgramVsnsReportedEvent;
import nan.produced.prism.core.program.application.service.ProgramDeploymentHandleApplicationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramDeploymentReportEventListener {

    private final ProgramDeploymentHandleApplicationService programDeploymentHandleApplicationService;

    @EventListener
    public void on(DeviceInternetProgramVsnsReportedEvent event) {
        if (event == null || event.deviceId() == null || event.deviceId() <= 0) {
            return;
        }

        if (event.vsnNames() == null || event.vsnNames().isEmpty()) {
            programDeploymentHandleApplicationService.clearProgramDeployment(event.deviceId());
            return;
        }

        programDeploymentHandleApplicationService.handleProgramDeploymentConsistency(event.userId(), event.deviceId(), event.vsnNames());
    }
}

