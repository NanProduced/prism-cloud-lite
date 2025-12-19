package nan.produced.prism.core.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import org.junit.jupiter.api.Test;

class DeviceActionPolymorphismTests {

    @Test
    void deviceActionTypeShouldBeRegisteredInDeviceActionBase() {
        JsonSubTypes jsonSubTypes = DeviceActionBase.class.getAnnotation(JsonSubTypes.class);
        assertThat(jsonSubTypes).isNotNull();

        Map<String, Class<?>> registeredByName = Arrays.stream(jsonSubTypes.value())
                .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));

        for (DeviceActionType type : DeviceActionType.values()) {
            assertThat(registeredByName)
                    .as("DeviceActionType.%s must be registered in @JsonSubTypes of DeviceActionBase", type)
                    .containsKey(type.name());
        }

        Schema schema = DeviceActionBase.class.getAnnotation(Schema.class);
        assertThat(schema).isNotNull();

        Set<Class<?>> oneOf = Set.of(schema.oneOf());
        for (DeviceActionType type : DeviceActionType.values()) {
            Class<?> actionClass = registeredByName.get(type.name());
            assertThat(oneOf)
                    .as("DeviceActionType.%s class must be included in DeviceActionBase @Schema(oneOf)", type)
                    .contains(actionClass);
        }

        Map<String, DiscriminatorMapping> discriminatorByValue = Arrays.stream(schema.discriminatorMapping())
                .collect(Collectors.toMap(DiscriminatorMapping::value, Function.identity()));

        for (DeviceActionType type : DeviceActionType.values()) {
            DiscriminatorMapping mapping = discriminatorByValue.get(type.name());
            assertThat(mapping)
                    .as("DeviceActionType.%s must be registered in DeviceActionBase @Schema(discriminatorMapping)", type)
                    .isNotNull();
            assertThat(mapping.schema())
                    .as("DeviceActionType.%s discriminator schema must match registered @JsonSubTypes class", type)
                    .isEqualTo(registeredByName.get(type.name()));
        }
    }
}

