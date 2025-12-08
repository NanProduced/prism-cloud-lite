package nan.produced.prism.device.boot.properties;

import nan.produced.prism.device.infrastructure.config.properties.DeviceProps;
import nan.produced.prism.device.infrastructure.websocket.config.PrismWebsocketConfigProps;
import nan.produced.prism.device.infrastructure.websocket.config.NettyWebsocketProps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        DeviceProps.class,
        NettyWebsocketProps.class,
        PrismWebsocketConfigProps.class
})
public class DeviceServicePropertiesConfiguration {
}
