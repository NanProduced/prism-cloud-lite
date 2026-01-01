# AI Assistant Function Calling — Tool Spec (Backend)

## Purpose
让 AI Assistant 在回答时可以“真正做事”：查询业务数据、做诊断与汇总，并把结果以结构化形式返回给前端（同时保持安全、可审计、可控）。

## Tool Contract (Model-facing)

每个 Tool 需要定义：
- `toolName`: 全局唯一（建议 `camelCase`）
- `description`: 给模型的使用说明（中文/英文可选）
- `input`: JSON object（严格 schema）
- `output`: JSON object（严格 schema，限制字段/数量）
- `auth`: 必须绑定 `userId`（来自 `CLOUD_AUTH`），禁止越权
- `audit`: 记录到 `assistant.assistant_tool_audit_log`

## Security Baseline

1. **最小必要输出**：不返回敏感字段（IP/密钥/内部 token/全量日志等）。
2. **强限制**：`limit`、时间范围、字段长度等都要有限制；默认更小。
3. **分级权限**：`admin` 才能访问的工具/字段，必须显式控制（后续可做 RBAC tool allowlist）。
4. **审计**：所有 tool 调用要落库，便于追责与优化。
5. **失败可解释**：输出错误原因+下一步建议（避免模型幻觉）。

## P0 Tools（建议优先实现/完善）

### 1) `searchDevices`
**意图**：按关键字搜索设备（名称/别名/编号等）。

Input:
```json
{ "keyword": "Lobby", "limit": 20 }
```

Output（示例）:
```json
{
  "items": [
    { "deviceId": 10001, "name": "Lobby TV", "online": true, "lastSeenAt": "2026-01-01T12:00:00Z" }
  ],
  "count": 1
}
```

Notes:
- `limit` 默认 20，最大 50。
- 不返回 IP、token、MAC 等敏感字段。

### 2) `getDeviceDetail`
**意图**：查看单台设备详情（用于诊断/答疑）。

Input:
```json
{ "deviceId": 10001 }
```

Output（示例）:
```json
{
  "deviceId": 10001,
  "name": "Lobby TV",
  "online": true,
  "lastSeenAt": "2026-01-01T12:00:00Z",
  "tags": ["store-01"]
}
```

Notes:
- 仅允许访问当前用户拥有/可见的设备。

### 3) `analyzeOfflineDevices`
**意图**：快速列出离线设备，并输出初步诊断建议。

Input:
```json
{ "limit": 50 }
```

Output（示例）:
```json
{
  "offlineCount": 3,
  "items": [
    { "deviceId": 10001, "name": "Lobby TV", "lastSeenAt": "2026-01-01T08:00:00Z" }
  ],
  "suggestions": [
    "检查设备网络是否可达",
    "检查设备是否断电或重启中"
  ]
}
```

### 4) `getErrorCodeHelp`
**意图**：把平台错误码映射到文档与排障步骤（RAG + 结构化输出）。

Input:
```json
{ "errorCode": "DEVICE_OFFLINE" }
```

Output（示例）:
```json
{
  "errorCode": "DEVICE_OFFLINE",
  "meaning": "设备离线",
  "steps": ["检查网络", "检查电源", "查看最近心跳时间"],
  "docs": [{ "title": "Device Troubleshooting", "url": "/help/guides/devices/troubleshooting" }]
}
```

## P1 Tools（价值更高，依赖更多业务数据）

### 5) `getRecentPublishFailures`
**意图**：定位“最近发布失败”的原因，给出修复建议。

Input:
```json
{ "sinceMinutes": 1440, "limit": 50 }
```

Output（示例）:
```json
{
  "count": 2,
  "items": [
    {
      "programId": 20001,
      "programName": "New Year Promo",
      "failedAt": "2026-01-01T11:00:00Z",
      "reason": "DEVICE_OFFLINE",
      "affectedDevices": [10001, 10002]
    }
  ]
}
```

### 6) `diagnoseDevice`
**意图**：综合诊断设备健康状况（心跳/同步/最近发布/告警）。

Input:
```json
{ "deviceId": 10001 }
```

Output（示例）:
```json
{
  "deviceId": 10001,
  "summary": "设备离线超过 4 小时，最近一次发布失败",
  "signals": {
    "online": false,
    "lastSeenAt": "2026-01-01T08:00:00Z",
    "lastPublishAt": "2026-01-01T07:30:00Z"
  },
  "recommendedActions": [
    "确认设备网络与电源",
    "重试发布或重新绑定设备"
  ]
}
```

## Implementation Notes (Spring AI)

后续切换到 Spring AI 原生 tool calling 时的建议做法：
1. 保留现有 `AssistantToolExecutor`（审计/权限/脱敏/限流）。
2. 新增 adapter：把每个 `AssistantTool` 暴露成 Spring AI `ToolCallback`（或 `@Tool`）。
3. 在 `OpenAiChatOptions` 中设置 `toolCallbacks(...)` / `toolNames(...)`，由模型决定是否调用。
4. Tool 执行结果仍通过现有 executor 返回，且继续写入 audit 表。

