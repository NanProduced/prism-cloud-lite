package nan.produced.prism.core.assistant.infrastructure.springai;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AssistantToolSpecs {

    public record ToolSpec(String name, String description, String inputJsonSchema) {
    }

    private final Map<String, ToolSpec> specs = Map.of(
            "searchDevices",
            new ToolSpec(
                    "searchDevices",
                    "Search devices by keyword for the current user. Use when the user asks to find/search devices.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "keyword": { "type": "string", "description": "Search keyword (name/serial/model)." },
                                "limit": { "type": "integer", "minimum": 1, "maximum": 50, "default": 20 }
                              },
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "getDeviceDetail",
            new ToolSpec(
                    "getDeviceDetail",
                    "Get a device detail by deviceId for the current user. Use when the user asks about a specific device status.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "deviceId": { "type": "integer", "description": "Device ID" }
                              },
                              "required": ["deviceId"],
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "analyzeOfflineDevices",
            new ToolSpec(
                    "analyzeOfflineDevices",
                    "List offline devices for the current user and return a summary. Use when the user asks about offline devices.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "limit": { "type": "integer", "minimum": 1, "maximum": 200, "default": 50 }
                              },
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "getErrorCodeHelp",
            new ToolSpec(
                    "getErrorCodeHelp",
                    "Explain a Prism Cloud Lite error code using Help Center docs context. Use when the user provides an error code or asks about an error message.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "errorCode": { "type": "string", "description": "Error code (e.g., DEVICE_OFFLINE)" }
                              },
                              "required": ["errorCode"],
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "getRecentPublishFailures",
            new ToolSpec(
                    "getRecentPublishFailures",
                    "List recent publish-related command failures (derived from device.command.finished FAILED messages). Use when the user asks about recent publish failures or why publish didn't reach devices.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "sinceMinutes": { "type": "integer", "minimum": 1, "maximum": 43200, "default": 1440 },
                                "limit": { "type": "integer", "minimum": 1, "maximum": 100, "default": 50 },
                                "actionTypePrefix": { "type": "string", "default": "PROGRAM", "description": "Filter by actionType prefix, e.g. PROGRAM" }
                              },
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "diagnoseDevice",
            new ToolSpec(
                    "diagnoseDevice",
                    "Diagnose a device using real device status and properties (online, last report time, storage, network). Use when the user asks to troubleshoot a specific device.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "deviceId": { "type": "integer", "description": "Device ID" }
                              },
                              "required": ["deviceId"],
                              "additionalProperties": false
                            }
                            """.trim()
            )
    );

    public ToolSpec get(String toolName) {
        if (toolName == null) {
            return null;
        }
        return specs.get(toolName);
    }

    public Map<String, ToolSpec> all() {
        return specs;
    }
}
