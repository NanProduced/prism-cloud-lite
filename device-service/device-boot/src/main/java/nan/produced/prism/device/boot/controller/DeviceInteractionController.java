package nan.produced.prism.device.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.api.DeviceInteractionApi;
import nan.produced.prism.device.api.dto.comand.DeviceApiCommand;
import nan.produced.prism.device.api.dto.comand.DeviceApiCommandConfirm;
import nan.produced.prism.device.api.dto.media.DeviceApiMedia;
import nan.produced.prism.device.api.dto.program.DeviceApiProgram;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "设备交互API", description = "终端设备与服务器直接进行交互的API")
public class DeviceInteractionController implements DeviceInteractionApi {

    /**
     * 上报终端信息，设备上报led_status到服务器。
     *
     * @param report 设备上报的led状态信息
     */
    @Operation(
            summary = "上报终端信息",
            description = "设备上报led_status到服务器",
            tags = {"终端上报"}
    )
    @Override
    public ResponseEntity<Void> reportDeviceProperties(String report) {
        return null;
    }

    @Override
    public List<DeviceApiCommand> getCommands(String clt_type, String device_num) {
        return List.of();
    }

    @Override
    public ResponseEntity<Void> confirmCommand(Integer post, DeviceApiCommandConfirm commandConfirm) {
        return null;
    }

    @Override
    public List<DeviceApiProgram> getPrograms(String clt_type) {
        return List.of();
    }

    @Override
    public List<DeviceApiMedia> getMedia(Integer parent) {
        return List.of();
    }

    @Override
    public ResponseEntity<Void> reportMediaPlayRecords(String report) {
        return null;
    }

    @Override
    public ResponseEntity<Void> reportProgramPlayRecords(String report) {
        return null;
    }

    @Override
    public String getSchedule() {
        return "";
    }

    @Override
    public ResponseEntity<Void> reportSensorData(String report) {
        return null;
    }

    @Override
    public ResponseEntity<Void> reportTerminalLog(String logs) {
        return null;
    }

    @Override
    public ResponseEntity<Void> reportScreenshot(HttpServletRequest request) {
        return null;
    }

    @Override
    public ResponseEntity<Void> reportDownloading(String report) {
        return null;
    }
}
