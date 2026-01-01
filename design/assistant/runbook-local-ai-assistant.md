# Runbook — Local AI Assistant (Chat + RAG + Function Calling)

## Prerequisites

- Postgres is running and core-service can connect.
- vLLM (or any OpenAI-compatible server) is running for chat:
  - example: `http://127.0.0.1:8000`
- Embedding service is running for RAG:
  - example: `http://127.0.0.1:7997`

## 1) Enable Spring AI engine (required for model-driven tool calling)

Set env:
- `ASSISTANT_CHAT_ENGINE=spring-ai`

Core uses `assistant.chat.llm.*` as the local default provider. You can also set BYOK providers via APIs.

## 2) Enable docs ingestion (optional, only when importing docs)

Set env:
- `ASSISTANT_RAG_INGEST_ENABLED=true`
- `ASSISTANT_RAG_INGEST_EXIT_AFTER_RUN=true` (ingest once and exit)
- `ASSISTANT_RAG_DOC_VERSION=dev`

Start core-service once. It will ingest and exit if configured.

## 3) Use BYOK (OpenAI) — optional but recommended for testing tool calling

### 3.1 Configure master key

Set env (base64 32 bytes):
- `PRISM_AI_CREDENTIALS_MASTER_KEY=...`

### 3.2 Store user key + set default provider

Call:
- `POST /api/v1/assistant/model-configs`
```json
{
  "provider": "openai",
  "model": "gpt-4o-mini",
  "enabled": true,
  "makeDefault": true,
  "apiKey": "sk-..."
}
```

Backend will route `/api/chat` to OpenAI for this user when `spring-ai` engine is enabled.

## 4) Validate function calling

Send a chat request (frontend or curl) that should trigger tools:
- “我有多少台离线设备？给我列出前 10 台并说明可能原因。”
- “设备 10001 的状态是什么？最后一次上报时间是多少？”
- “帮我诊断设备 10001，给出可操作的排查步骤。”
- “搜索设备 Lobby”
- “我遇到了错误码 DEVICE_OFFLINE，这是什么意思？怎么处理？”
- “最近发布失败有哪些？为什么节目没下发成功？”

Expected behavior (SSE stream):
- You will see tool chunks:
  - `tool-input-available`
  - `tool-output-available` / `tool-output-error`
- Then a text answer that references tool results.
- Ends with `[DONE]`.

## Notes

- Tool calls are audited into `assistant.assistant_tool_audit_log`.
- Current exposed tools (P0): `searchDevices`, `getDeviceDetail`, `analyzeOfflineDevices`.
