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
            ),
            "diagnoseDeviceCommand",
            new ToolSpec(
                    "diagnoseDeviceCommand",
                    "Diagnose a device command status/failure for the current user and explain why it is pending/failed/expired/confirmed. Use when the user asks why a command didn't execute.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "commandLogId": { "type": "integer", "description": "Device command log ID (internal; usually supplied by UI selection)." },
                                "operationId": { "type": "string", "description": "Operation ID / command ID (UUID string). (internal; usually hidden from users)" },
                                "deviceId": { "type": "integer", "description": "Device ID (internal; usually supplied by UI selection)." },
                                "deviceName": { "type": "string", "description": "Device name keyword (user-visible)." },
                                "actionType": { "type": "string", "description": "Device action type enum name, e.g. SCREENSHOT/POWER/PROGRAM_PUBLISH." },
                                "sinceMinutes": { "type": "integer", "minimum": 1, "maximum": 43200, "default": 1440 },
                                "limit": { "type": "integer", "minimum": 1, "maximum": 30, "default": 10 }
                              },
                              "additionalProperties": false
                            }
                            """.trim()
            ),
            "searchCommandLogs",
            new ToolSpec(
                    "searchCommandLogs",
                    "Search device command logs for the current user by device name/time/action/status and return candidates for interactive picking. Use when the user asks to find which command to diagnose.",
                    """
                            {
                              "type": "object",
                              "properties": {
                                "deviceName": { "type": "string", "description": "Device name keyword (user-visible)." },
                                "deviceId": { "type": "integer", "description": "Device ID (internal; usually supplied by UI selection)." },
                                "actionType": { "type": "string", "description": "Device action type enum name, e.g. SCREENSHOT/POWER/PROGRAM_PUBLISH." },
                                "statuses": {
                                  "type": "array",
                                  "items": { "type": "string" },
                                  "description": "Device command statuses, e.g. PUBLISHED/CONFIRMED/COMPLETED/EXPIRED/FAILED."
                                },
                                "accepted": { "type": "boolean" },
                                "covered": { "type": "boolean" },
                                "sendMethod": { "type": "string", "description": "Send method filter, e.g. websocket/http." },
                                "sinceMinutes": { "type": "integer", "minimum": 1, "maximum": 43200, "default": 1440 },
                                "limit": { "type": "integer", "minimum": 1, "maximum": 30, "default": 10 }
                              },
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
