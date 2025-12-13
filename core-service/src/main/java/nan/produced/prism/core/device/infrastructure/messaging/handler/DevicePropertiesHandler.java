package nan.produced.prism.core.device.infrastructure.messaging.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.util.BeanUtils;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.application.port.outbound.DevicePropertiesPort;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.DeviceProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static nan.produced.prism.core.common.exception.ErrorCode.DEVICE_NOT_FOUND_IN_CORE;

/**
 * 设备属性处理
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevicePropertiesHandler implements DevicePropertiesPort {

    private final DeviceRepository deviceRepository;

    @Override
    public void handleDeviceProperties(Long deviceId, String properties, String traceId) {
        LocalDateTime now = LocalDateTime.now();
        DeviceProperties deviceProperties = JsonUtils.fromJson(properties, DeviceProperties.class);
        populateReportTime(deviceProperties, System.currentTimeMillis() / 1000);
        DeviceEntity existingDevice = deviceRepository.findByDeviceId(deviceId);
        if (existingDevice == null) {
            throw new BizException(DEVICE_NOT_FOUND_IN_CORE, "device not find: deviceId = " + deviceId);
        }
        DeviceEntity updateDevice = handleRedundantProperties(existingDevice, deviceProperties);
        updateDevice.setLastReportTime(now);
        deviceRepository.updateDeviceProperties(updateDevice);
    }

    private DeviceEntity handleRedundantProperties(DeviceEntity existingDevice, DeviceProperties properties) {
        if (properties == null) return null;

        // info.info
        if (Optional.ofNullable(properties.getInfo().getInfo()).isPresent()) {
            DeviceProperties.Info info = properties.getInfo().getInfo();
            if (info.getVername() != null) {
                existingDevice.setVersion(info.getVername());
            }
            if (info.getModel() != null) {
                existingDevice.setModel(info.getModel());
            }
            if (info.getStorage() != null) {
                existingDevice.setTotalStorage(info.getStorage().getTotal());
                existingDevice.setFreeStorage(info.getStorage().getFree());
            }
            if (info.getPlaying() != null) {
                existingDevice.setPlayingProgram(info.getPlaying().getName());
            }
        }

        // dimension
        if (Optional.ofNullable(properties.getDimension()).isPresent()) {
            int realWidth = properties.getDimension().getReal_width();
            int realHeight = properties.getDimension().getReal_height();
            existingDevice.setResolution(realWidth + " x " + realHeight);
        }

        // brightnessandcolortemp
        if (Optional.ofNullable(properties.getBrightnessandcolortemp()).isPresent()) {
            existingDevice.setBrightness(properties.getBrightnessandcolortemp().getBrightness() / 100 * 255);
        }

        // 合并、替换Properties
        BeanUtils.copyNonNullProperties(properties, existingDevice.getProperties());

        return existingDevice;

    }

    private void populateReportTime(DeviceProperties properties, long serverTimestamp) {
        if (properties ==  null) return;

        Field[] fields = DeviceProperties.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.trySetAccessible();
                Object fieldValue = field.get(properties);
                if (fieldValue != null) {
                    fillReportTimeIfExist(fieldValue, serverTimestamp);
                }
            } catch (IllegalAccessException e) {
                log.warn("DeviceProperties - 处理reportTime字段失败: field={}", field.getName(), e);
            }
        }
    }

    /**
     * 检查对象是否包含reportTime字段，如果包含则填充
     */
    private void fillReportTimeIfExist(Object obj, long timestamp) {
        try {
            Field reportTimeField = obj.getClass().getDeclaredField("reportTime");
            reportTimeField.trySetAccessible();
            reportTimeField.setLong(obj, timestamp);
        } catch (NoSuchFieldException e) {
            // ignore
            // 该对象没有reportTime字段
        } catch (IllegalAccessException e) {
            log.warn("DeviceProperties - 填充reportTime字段失败: class={}", obj.getClass().getName(), e);
        }
    }
}
