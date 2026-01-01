package nan.produced.prism.core.export.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.export.api.ExportType;
import nan.produced.prism.core.export.api.dto.ExportFieldDefinition;
import nan.produced.prism.core.export.api.dto.ExportValueType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExportSchemaRegistry {

    public List<ExportFieldDefinition> fields(ExportType type) {
        return switch (type) {
            case DEVICE_LOGS -> List.of(
                    ExportFieldDefinition.builder().key("id").headerI18nKey("export.field.id").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceId").headerI18nKey("export.field.deviceId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceName").headerI18nKey("export.field.deviceName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("operationId").headerI18nKey("export.field.operationId").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("level").headerI18nKey("export.field.level").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("logType").headerI18nKey("export.field.logType").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("categories").headerI18nKey("export.field.categories").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("description").headerI18nKey("export.field.description").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceTimeRaw").headerI18nKey("export.field.deviceTimeRaw").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("reportTime").headerI18nKey("export.field.reportTime").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("createdAt").headerI18nKey("export.field.createdAt").valueType(ExportValueType.DATETIME).build()
            );
            case COMMAND_LOGS -> List.of(
                    ExportFieldDefinition.builder().key("id").headerI18nKey("export.field.id").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceId").headerI18nKey("export.field.deviceId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceName").headerI18nKey("export.field.deviceName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("operationId").headerI18nKey("export.field.operationId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("actionType").headerI18nKey("export.field.actionType").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("trackingLevel").headerI18nKey("export.field.trackingLevel").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("status").headerI18nKey("export.field.status").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("accepted").headerI18nKey("export.field.accepted").valueType(ExportValueType.BOOLEAN).build(),
                    ExportFieldDefinition.builder().key("covered").headerI18nKey("export.field.covered").valueType(ExportValueType.BOOLEAN).build(),
                    ExportFieldDefinition.builder().key("sendMethod").headerI18nKey("export.field.sendMethod").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("queuedId").headerI18nKey("export.field.queuedId").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("errorMessage").headerI18nKey("export.field.errorMessage").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("createdAt").headerI18nKey("export.field.createdAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("updatedAt").headerI18nKey("export.field.updatedAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("payload").headerI18nKey("export.field.payload").valueType(ExportValueType.JSON).build()
            );
            case DEVICE_ONLINE_SESSIONS -> List.of(
                    ExportFieldDefinition.builder().key("sessionId").headerI18nKey("export.field.sessionId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceId").headerI18nKey("export.field.deviceId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceName").headerI18nKey("export.field.deviceName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("onlineAt").headerI18nKey("export.field.onlineAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("offlineAt").headerI18nKey("export.field.offlineAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveOnlineAt").headerI18nKey("export.field.effectiveOnlineAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveOfflineAt").headerI18nKey("export.field.effectiveOfflineAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("onlineSecondsInRange").headerI18nKey("export.field.onlineSecondsInRange").valueType(ExportValueType.LONG).build()
            );
            case PROGRAM_PLAY_SESSIONS -> List.of(
                    ExportFieldDefinition.builder().key("id").headerI18nKey("export.field.id").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceId").headerI18nKey("export.field.deviceId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceName").headerI18nKey("export.field.deviceName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("lan").headerI18nKey("export.field.lan").valueType(ExportValueType.BOOLEAN).build(),
                    ExportFieldDefinition.builder().key("lanProgramId").headerI18nKey("export.field.lanProgramId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("programId").headerI18nKey("export.field.programId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("releaseVersion").headerI18nKey("export.field.releaseVersion").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("programName").headerI18nKey("export.field.programName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("programVsn").headerI18nKey("export.field.programVsn").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("startAt").headerI18nKey("export.field.startAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("endAt").headerI18nKey("export.field.endAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveStartAt").headerI18nKey("export.field.effectiveStartAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveEndAt").headerI18nKey("export.field.effectiveEndAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("playSecondsInRange").headerI18nKey("export.field.playSecondsInRange").valueType(ExportValueType.LONG).build(),
                    ExportFieldDefinition.builder().key("createdAt").headerI18nKey("export.field.createdAt").valueType(ExportValueType.DATETIME).build()
            );
            case MEDIA_PLAY_SESSIONS -> List.of(
                    ExportFieldDefinition.builder().key("id").headerI18nKey("export.field.id").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceId").headerI18nKey("export.field.deviceId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("deviceName").headerI18nKey("export.field.deviceName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("lan").headerI18nKey("export.field.lan").valueType(ExportValueType.BOOLEAN).build(),
                    ExportFieldDefinition.builder().key("mediaId").headerI18nKey("export.field.mediaId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("itemType").headerI18nKey("export.field.itemType").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("resOriginName").headerI18nKey("export.field.resOriginName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("resMd5Name").headerI18nKey("export.field.resMd5Name").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("programId").headerI18nKey("export.field.programId").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("releaseVersion").headerI18nKey("export.field.releaseVersion").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("programName").headerI18nKey("export.field.programName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("programVsn").headerI18nKey("export.field.programVsn").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("pageName").headerI18nKey("export.field.pageName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("pageIndex").headerI18nKey("export.field.pageIndex").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("regionName").headerI18nKey("export.field.regionName").valueType(ExportValueType.TEXT).build(),
                    ExportFieldDefinition.builder().key("regionIndex").headerI18nKey("export.field.regionIndex").valueType(ExportValueType.INT).build(),
                    ExportFieldDefinition.builder().key("startAt").headerI18nKey("export.field.startAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("endAt").headerI18nKey("export.field.endAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveStartAt").headerI18nKey("export.field.effectiveStartAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("effectiveEndAt").headerI18nKey("export.field.effectiveEndAt").valueType(ExportValueType.DATETIME).build(),
                    ExportFieldDefinition.builder().key("playSecondsInRange").headerI18nKey("export.field.playSecondsInRange").valueType(ExportValueType.LONG).build(),
                    ExportFieldDefinition.builder().key("reportedDuration").headerI18nKey("export.field.reportedDuration").valueType(ExportValueType.LONG).build()
            );
        };
    }
}
