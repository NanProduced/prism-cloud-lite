package nan.produced.prism.device.application.port.outbound.event;

import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.messaging.DeviceEventRoutingKeys;

public interface DeviceEventPublisherPort {

    void publish(String routingKey, DeviceEventMessage message);

    default void publishStatus(String type, DeviceEventMessage message) {
        publish(DeviceEventRoutingKeys.status(type), message);
    }

    default void publishCommand(String type, DeviceEventMessage message) {
        publish(DeviceEventRoutingKeys.command(type), message);
    }

    default void publishReport(String type, DeviceEventMessage message) {
        publish(DeviceEventRoutingKeys.report(type), message);
    }
}

