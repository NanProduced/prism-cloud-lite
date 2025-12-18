package nan.produced.prism.device.infrastructure.websocket.processor.v11;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.domain.websocket.WsMessageProcessingContext;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketErrorType;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketMessage;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketMessageType;
import nan.produced.prism.device.application.port.inbound.command.DeviceCommandUseCase;
import nan.produced.prism.device.application.port.inbound.status.DeviceReportUseCase;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessException;
import nan.produced.prism.device.common.utils.JsonUtils;
import nan.produced.prism.device.infrastructure.websocket.connection.DeviceWsSession;
import nan.produced.prism.device.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import nan.produced.prism.device.infrastructure.websocket.processor.v11.dto.V11CommandResp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * v11协议 - 操作分发路由
 *
 * @author Nan
 */
@Slf4j
@Component
public class V11OperationHandlerRouter {

    private final DeviceCommandUseCase deviceCommandUseCase;
    private final DeviceReportUseCase deviceReportUseCase;
    private final V11WebsocketDtoConverter dtoConverter;

    private final Executor websocketBusinessExecutor;

    public V11OperationHandlerRouter(DeviceCommandUseCase deviceCommandUseCase,
                                     DeviceReportUseCase deviceReportUseCase,
                                     V11WebsocketDtoConverter dtoConverter,
                                     @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        this.deviceCommandUseCase = deviceCommandUseCase;
        this.deviceReportUseCase = deviceReportUseCase;
        this.dtoConverter = dtoConverter;
        this.websocketBusinessExecutor = websocketBusinessExecutor;
    }

    /**
     * 空JSON结构体
     */
    private static final String EMPTY_JSON = "{}";
    
    public void handleMessageByType(WsMessageProcessingContext context, V11WebsocketMessage  message) {
        V11WebsocketMessageType messageType = V11WebsocketMessageType.fromId(message.getType());
        if (messageType == null) {
            throw new BusinessException(BusinessErrorCode.WS_INVALID_MESSAGE_TYPE);
        }

        switch (messageType) {

            // 已弃用，适配旧设备
            case HEARTBEAT -> context.sendMessage("");

            // 指令获取
            case COMMAND -> handleGetCommand(context, message.getMessageId());

            // 排程获取
            case SCHEDULE -> handleGetSchedule(context, message.getMessageId());

            // 节目获取
            case PROGRAMS -> handleGetProgram(context, message.getMessageId());

            // 指令确认
            case CONFIRM_COMMAND -> handleConfirmCommand(context, message);

            // 设备属性上报
            case STATUS_REPORT -> handlePropertiesReport(context, message);

            // 下载进度上报
            case DOWNLOAD_STATUS -> handleDownloadingReport(context, message);

            // 素材播放记录上报
            case MEDIA_RECORD -> handleMediaPlayRecordReport(context, message);

            // 传感器数据上报
            case MONITOR_REPORT -> handleSensorDataReport(context, message);

            // 终端日志上报
            case LOG_REPORT -> handleDeviceLogReport(context, message);

            // 节目播放记录上报
            case PROGRAM_RECORD -> handleProgramPlayRecordReport(context, message);

            default -> throw new BusinessException(BusinessErrorCode.WS_INVALID_MESSAGE_TYPE);
        }
    }

    /* =================== 指令类型对应的处理方法 =================== */

    /**
     * 主动推送指令给设备（连接建立时调用）
     * 异步化处理：避免Redis查询阻塞EventLoop线程
     *
     * <p>与handleGetCommand的区别：</p>
     * <ul>
     *   <li>handleGetCommand：响应设备的主动请求，receiptId = messageId</li>
     *   <li>pushCommandsOnConnection：服务器主动推送，receiptId = null</li>
     * </ul>
     *
     * @param context 消息处理上下文，包含设备ID等信息
     */
    public void pushCommandsOnConnection(WsMessageProcessingContext context) {
        Long deviceId = context.getDeviceId();

        // 异步执行Redis查询操作，避免阻塞EventLoop线程
        // getPendingCommands可能涉及多次Redis操作：List查询 + 批量Get操作
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // 执行可能耗时的Redis查询操作
                        List<DeviceCommand> pendingCommands = deviceCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.toV11CommandResp(pendingCommands);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令异常】deviceId:{}", deviceId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((commandResponses, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    DeviceWsSession session = (DeviceWsSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 成功获取指令，发送响应
                                // receiptId为null，表示服务器主动推送（区别于设备请求的响应）
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageType.COMMAND.getId(), null, commandResponses));
                                log.info("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令成功】deviceId:{}, commandIds:{}",
                                        deviceId, commandResponses.stream().map(V11CommandResp::getId).toList());
                            } else {
                                // 处理异常，记录错误但不发送错误响应（避免干扰正常流程）
                                log.error("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令失败】deviceId:{}", deviceId, throwable);
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #PUSH_COMMAND#【发送推送消息异常】deviceId:{}", deviceId, e);
                        }
                    });
                });
    }

    /**
     * 处理获取指令的命令。
     * 异步化处理：避免Redis查询阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetCommand(WsMessageProcessingContext context, Integer messageId) {
        Long deviceId = context.getDeviceId();
        // 异步执行Redis查询操作，避免阻塞EventLoop线程
        // getPendingCommands可能涉及多次Redis操作：List查询 + 批量Get操作
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // 执行可能耗时的Redis查询操作
                        List<DeviceCommand> pendingCommands = deviceCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.toV11CommandResp(pendingCommands);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #GET_COMMENT#【获取指令异常】deviceId:{}", deviceId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((commandResponses, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    DeviceWsSession session = (DeviceWsSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 成功获取指令，发送响应
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageType.COMMAND.getId(), messageId, commandResponses));
                                log.info("V11Router -ws- #GET_COMMENT#【获取指令成功】deviceId:{}, commandIds:{}",
                                        deviceId, commandResponses.stream().map(V11CommandResp::getId).toList());
                            } else {
                                // 处理异常，发送错误响应
                                log.error("V11Router -ws- #GET_COMMENT#【获取指令失败】deviceId:{}", deviceId, throwable);
                                context.sendMessage(V11WebsocketMessage.generateErrorContent(
                                        V11WebsocketErrorType.SERVER_ERROR, messageId, "获取指令失败"));
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #GET_COMMENT#【发送响应异常】deviceId:{}", deviceId, e);
                        }
                    });
                });
    }

    /**
     * 处理获取排程指令
     * 异步化处理：避免RPC调用阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId   消息ID，用于响应时关联请求
     */
    private void handleGetSchedule(WsMessageProcessingContext context, Integer messageId) {
        // todo: 排程模块待实现
    }

    /**
     * 处理获取节目指令
     * 异步化处理：避免RPC调用阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId   消息ID，用于响应时关联请求
     */
    private void handleGetProgram(WsMessageProcessingContext context, Integer messageId) {
        // todo: 节目模块待实现
    }

    /**
     * 处理指令确认消息
     * 异步化处理：避免Redis删除操作和事件发布阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleConfirmCommand(WsMessageProcessingContext context, V11WebsocketMessage message) {
        // 快速参数验证，在EventLoop线程中完成
        if (Objects.isNull(message.getData())) {
            throw new BusinessException(BusinessErrorCode.WS_INVALID_MESSAGE_DATA);
        }
        String dataStr = JsonUtils.toJson(message.getData());
        int commandId = JsonUtils.getIntValue(dataStr, "parent", 0);
        String content = JsonUtils.getStringValue(dataStr, "content", "");

        if (commandId <= 0) {
            throw new BusinessException(BusinessErrorCode.WS_INVALID_MESSAGE_DATA);
        }

        Long deviceId = context.getDeviceId();
        Integer messageId = message.getMessageId();

        // 异步执行指令确认操作，避免阻塞EventLoop线程
        // confirmCommandExecution涉及：Redis删除操作 + 事件发布
        CompletableFuture
                .runAsync(() -> {
                    try {
                        // 执行可能耗时的Redis删除和事件发布操作
                        deviceCommandUseCase.confirmCommand(deviceId, commandId, content);
                        log.info("V11Router -ws- #CONFIRM_COMMENT#【确认指令业务处理成功】deviceId:{}, commandId:{}",
                                deviceId, commandId);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #CONFIRM_COMMENT#【确认指令业务处理失败】deviceId:{}, commandId:{}",
                                deviceId, commandId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((result, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    DeviceWsSession session = (DeviceWsSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 确认成功，发送成功响应
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageType.CONFIRM_COMMAND.getId(), messageId));
                                log.info("V11Router -ws- #CONFIRM_COMMENT#【确认指令成功】deviceId:{}, commandId:{}",
                                        deviceId, commandId);
                            } else {
                                // 确认失败，发送错误响应
                                log.error("V11Router -ws- #CONFIRM_COMMENT#【确认指令失败】deviceId:{}, commandId:{}",
                                        deviceId, commandId, throwable);
                                context.sendMessage(V11WebsocketMessage.generateErrorContent(
                                        V11WebsocketErrorType.INVALID_COMMENT_ID, messageId,
                                        "confirm command failed: " + commandId));
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #CONFIRM_COMMENT#【发送确认响应异常】deviceId:{}, commandId:{}",
                                    deviceId, commandId, e);
                        }
                    });
                });
    }

    /**
     * 处理设备属性上报的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handlePropertiesReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushDeviceProperties(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #STATUS_REPORT#【上报终端状态】deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.STATUS_REPORT.getId(), message.getMessageId()));
    }

    /**
     * 处理上报下载状态的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleDownloadingReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushDownloadingReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #DOWNLOADING_REPORT#【上报下载状态】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.DOWNLOAD_STATUS.getId(), message.getMessageId()));
    }

    /**
     * 处理素材播放记录报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleMediaPlayRecordReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushMediaPlayRecordReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #MEDIA_PLAY_RECORD_REPORT#【上报素材播放记录】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.MEDIA_RECORD.getId(), message.getMessageId()));
    }

    /**
     * 处理节目播放记录报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleProgramPlayRecordReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushProgramPlayRecordReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #PROGRAM_PLAY_RECORD_REPORT#【上报节目播放记录】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.PROGRAM_RECORD.getId(), message.getMessageId()));
    }

    /**
     * 处理终端日志报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleDeviceLogReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushDeviceLog(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #DEVICE_LOG_REPORT#【上报设备日志】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.LOG_REPORT.getId(), message.getMessageId()));
    }

    /**
     * 处理传感器数据报告的命令。
     * V11协议：data字段为List<SensorReport>格式
     *
     * <p>注意：由于V11WebsocketMessage.data是Object类型，JSON反序列化后会产生ArrayList<LinkedHashMap>
     * 而不是ArrayList<SensorReport>，因此需要使用JsonUtils.convertValue()进行类型转换</p>
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleSensorDataReport(WsMessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        deviceReportUseCase.asyncPushSensorReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #SENSOR_DATA_REPORT#【上报传感器数据】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageType.MONITOR_REPORT.getId(), message.getMessageId()));
    }

}
