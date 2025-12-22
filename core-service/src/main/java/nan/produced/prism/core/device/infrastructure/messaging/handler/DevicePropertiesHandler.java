package nan.produced.prism.core.device.infrastructure.messaging.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.util.BeanUtils;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.application.port.outbound.DevicePropertiesPort;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.DeviceNetworkType;
import nan.produced.prism.core.device.domain.DeviceProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
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
    public void handleDeviceProperties(Long deviceId, DeviceProperties deviceProperties, String traceId) {
        LocalDateTime now = LocalDateTime.now();
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
        if (existingDevice == null) {
            return null;
        }
        if (properties == null) {
            return existingDevice;
        }

        // info.info
        DeviceProperties.InfoWrapper infoWrapper = properties.getInfo();
        DeviceProperties.Info info = infoWrapper != null ? infoWrapper.getInfo() : null;
        if (info != null) {
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
        DeviceProperties.Dimension dimension = properties.getDimension();
        if (dimension != null) {
            int realWidth = dimension.getReal_width();
            int realHeight = dimension.getReal_height();
            existingDevice.setResolution(realWidth + " x " + realHeight);
        }

        // network-type
        DeviceProperties.IfStatus ifStatus = properties.getIfStatus();
        List<DeviceProperties.IfStatus.NetInterface> types = ifStatus != null ? ifStatus.getTypes() : null;
        if (types != null && !types.isEmpty()) {
            types.stream()
                    .filter(e -> e != null && e.getEnabled() == 1)
                    .findFirst()
                    .ifPresent(netInterface -> {
                        DeviceNetworkType networkType = DeviceNetworkType.getByName(netInterface.getType());
                        if (networkType != null) {
                            existingDevice.setNetworkType(networkType.getCode());
                        }
                    });
        }

        // brightnessandcolortemp
        DeviceProperties.BrightnessAndColorTemp brightnessAndColorTemp = properties.getBrightnessandcolortemp();
        if (brightnessAndColorTemp != null) {
            existingDevice.setBrightness(Math.round(brightnessAndColorTemp.getBrightness() * 100f / 255f));
        }

        // 合并、替换Properties
        if (existingDevice.getProperties() == null) {
            existingDevice.setProperties(new DeviceProperties());
        }
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
