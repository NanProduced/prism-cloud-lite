package nan.produced.prism.device.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.api.DeviceInteractionApi;
import nan.produced.prism.device.api.dto.comand.DeviceApiCommand;
import nan.produced.prism.device.api.dto.comand.DeviceApiCommandConfirm;
import nan.produced.prism.device.api.dto.media.DeviceApiMedia;
import nan.produced.prism.device.api.dto.program.DeviceApiProgram;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.port.inbound.command.DeviceCommandUseCase;
import nan.produced.prism.device.application.port.inbound.status.DeviceReportUseCase;
import nan.produced.prism.device.boot.integration.command.DeviceCommandConverter;
import nan.produced.prism.device.common.utils.CommonUtils;
import nan.produced.prism.device.infrastructure.internal.core.CoreProgramDistributionService;
import nan.produced.prism.device.infrastructure.internal.core.CoreScheduleDistributionService;
import nan.produced.prism.device.infrastructure.internal.core.dto.CoreDeviceProgramDTO;
import nan.produced.prism.device.infrastructure.internal.core.dto.CoreDeviceProgramMediaDTO;
import nan.produced.prism.device.common.exception.DeviceResponseException;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.infrastructure.internal.core.dto.DeviceProgramDTO;
import nan.produced.prism.device.infrastructure.storage.s3.DeviceScreenshotS3Uploader;
import nan.produced.prism.device.infrastructure.security.DevicePrincipal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "设备交互API", description = "终端设备与服务器直接进行交互的API")
public class DeviceInteractionController implements DeviceInteractionApi {

    private final DeviceReportUseCase deviceReportUseCase;

    private final DeviceCommandUseCase deviceCommandUseCase;
    private final DeviceCommandConverter deviceCommandConverter;
    private final DeviceProgramConverter deviceProgramConverter;
    private final DeviceScreenshotS3Uploader deviceScreenshotS3Uploader;
    private final CoreProgramDistributionService coreProgramDistributionService;
    private final CoreScheduleDistributionService coreScheduleDistributionService;

    private static final DateTimeFormatter WP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushDeviceProperties(devicePrincipal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 获取终端指令的方法。此方法允许终端通过HTTP方式请求其待执行的指令。
     *
     * @param cltType 客户端类型，当前实现中未使用该参数
     * @param deviceNum 设备编号，当前实现中未使用该参数
     * @return 待执行的设备API命令列表；如果发生异常，则返回一个空列表
     */
    @Operation(
            summary = "终端获取指令",
            description = "终端通过HTTP方式获取指令",
            tags = {"终端指令"}
    )
    @Override
    public List<DeviceApiCommand> getCommands(String cltType, String deviceNum) {
        // cltType、deviceNum这两个参数没用，历史问题
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            List<DeviceCommand> pendingCommands = deviceCommandUseCase.getPendingCommands(devicePrincipal.getDeviceId());
            return deviceCommandConverter.toDeviceApiCommand(pendingCommands);
        } catch (Exception e) {
            log.error("DeviceCommand - 获取设备指令异常, deviceNum: {}", devicePrincipal.getDeviceId(), e);
            return List.of();
        }
    }

    @Operation(
            summary = "终端确认指令",
            description = "终端通过HTTP方式确认指令",
            tags = {"终端指令"}
    )
    @Override
    public ResponseEntity<Void> confirmCommand(Integer post, DeviceApiCommandConfirm commandConfirm) {
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (commandConfirm.getParent() == null) {
            log.warn("DeviceCommand - 终端确认指令参数错误, deviceNum: {}", devicePrincipal.getDeviceId());
            throw new DeviceResponseException(BusinessErrorCode.PARAMETER_MISSING);
        }
        try {
            deviceCommandUseCase.confirmCommand(devicePrincipal.getDeviceId(), commandConfirm.getParent(), commandConfirm.getContent());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("DeviceCommand - 终端确认指令异常, deviceNum: {}", devicePrincipal.getDeviceId(), e);
            throw  new DeviceResponseException(BusinessErrorCode.SYSTEM_ERROR);
        }
    }

    @Operation(
            summary = "终端获取节目",
            description = "终端通过HTTP方式获取节目",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiProgram> getPrograms(String clt_type) {
        DevicePrincipal devicePrincipal = (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = devicePrincipal.getDeviceId();
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

        List<DeviceProgramDTO> programs = coreProgramDistributionService.listDevicePrograms(deviceId, baseUrl);
        return deviceProgramConverter.toDeviceApiProgram( programs);
    }

    @Operation(
            summary = "终端获取素材",
            description = "终端通过HTTP方式获取素材",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiMedia> getMedia(Integer parent) {
        if (parent == null) {
            return List.of();
        }
        DevicePrincipal devicePrincipal = (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = devicePrincipal.getDeviceId();

        // /wp-json/wp/v2/media 必须返回可直接下载的 URL（CDN/对象存储公开地址），设备端不支持后端流式代理下载。
        List<CoreDeviceProgramMediaDTO> media = coreProgramDistributionService.listDeviceProgramMedia(deviceId, parent);
        if (media == null || media.isEmpty()) {
            return List.of();
        }

        List<DeviceApiMedia> list = new ArrayList<>();
        for (CoreDeviceProgramMediaDTO item : media) {
            if (item == null || !StringUtils.isNotBlank(item.getUrl()) || item.getSizeBytes() == null || item.getSizeBytes() <= 0) {
                continue;
            }
            list.add(DeviceApiMedia.builder()
                    .attachmentFileSize(CommonUtils.toIntSize(item.getSizeBytes()))
                    .sourceUrl(item.getUrl())
                    .build());
        }
        return list;
    }

    @Operation(
            summary = "终端上报素材播放记录",
            description = "终端上报素材播放记录",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportMediaPlayRecords(String report) {
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushMediaPlayRecordReport(devicePrincipal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            summary = "终端上报节目播放记录",
            description = "终端上报节目播放记录",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportProgramPlayRecords(String report) {
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushProgramPlayRecordReport(devicePrincipal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            summary = "终端获取排程",
            description = "终端获取节目排程",
            tags = {"终端节目"}
    )
    @Override
    public String getSchedule() {
        DevicePrincipal devicePrincipal = (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = devicePrincipal.getDeviceId();
        return coreScheduleDistributionService.getDeviceScheduleJson(deviceId);
    }

    @Operation(
            summary = "终端上报传感器数据",
            description = "终端上报传感器数据",
            tags = {"终端监控"}
    )
    @Override
    public ResponseEntity<Void> reportSensorData(String report) {
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushSensorReport(devicePrincipal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            summary = "终端上报终端日志",
            description = "终端上报终端日志",
            tags = {"终端监控"}
    )
    @Override
    public ResponseEntity<Void> reportTerminalLog(String logs) {
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushDeviceLog(devicePrincipal.getDeviceId(), logs);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @Operation(
            summary = "终端上报终端截图",
            description = "终端上报终端截图",
            tags = {"终端监控"}
    )
    @Override
    public ResponseEntity<Void> reportScreenshot(HttpServletRequest request) {
        DevicePrincipal devicePrincipal = (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            if (bytes.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String contentType = request.getContentType();
            var result = deviceScreenshotS3Uploader.upload(devicePrincipal.getDeviceId(), bytes, contentType);
            deviceReportUseCase.asyncPushScreenshotReport(
                    devicePrincipal.getDeviceId(),
                    result.s3Key(),
                    result.sizeBytes(),
                    result.contentType());
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            log.error("Screenshot - 截图上传失败, deviceId={}", devicePrincipal.getDeviceId(), e);
            throw new DeviceResponseException(BusinessErrorCode.OPERATION_FAILED);
        }
    }

    @Operation(
            summary = "终端上报下载进度",
            description = "终端上报下载进度",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportDownloading(String report) {
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        DevicePrincipal devicePrincipal= (DevicePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        deviceReportUseCase.asyncPushDownloadingReport(devicePrincipal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
