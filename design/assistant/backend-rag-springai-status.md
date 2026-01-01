# Prism Cloud Lite AI Assistant — Backend Status & Next Steps

## Current status (what’s done)

### 1) Help Center docs → RAG ingestion → vector DB
- Docs roots (default):
  - `../docs/help` (zh)
  - `../docs/help-en` (en)
- Ingestion stores:
  - documents metadata
  - chunked text
  - embeddings (pgvector)
- Retrieval:
  - Chat will retrieve topK chunks and inject into system prompt.

### 2) Chat API (stable frontend contract)
- Endpoint: `POST /api/chat`
- Response: `text/event-stream`
  - Each SSE `data:` is a JSON object (a **UIMessageChunk** compatible shape).
  - Stream ends with `data: [DONE]`.
- Tool events already supported in SSE protocol:
  - `tool-input-available`
  - `tool-output-available`
  - `tool-output-error`

### 3) BYOK (Bring Your Own Key) — user model config storage
- DB schema: `assistant.user_ai_model_config`
- Encryption: AES-GCM, master key env:
  - `PRISM_AI_CREDENTIALS_MASTER_KEY` (base64 32 bytes)
- REST API:
  - `GET /api/v1/assistant/model-configs`
  - `POST /api/v1/assistant/model-configs`
  - `POST /api/v1/assistant/model-configs/{provider}/default`
  - `DELETE /api/v1/assistant/model-configs/{provider}`
- Providers (current allowlist):
  - `local-vllm`
  - `openai`
  - `gemini` (reserved; BYOK baseUrl/model needs final confirmation)

### 4) Function calling foundation (server-side tools)
- Tool execution is server-side and audited:
  - Audit table: `assistant.assistant_tool_audit_log`
- Existing example tools:
  - `searchDevices`
  - `getDeviceDetail`
  - `analyzeOfflineDevices`

### 5) Spring AI integration (incremental switch)
- Config switch:
  - `assistant.chat.engine=${ASSISTANT_CHAT_ENGINE:raw}` (`raw|spring-ai`)
- When `spring-ai`:
  - Uses Spring AI `OpenAiChatModel.stream()` against OpenAI-compatible endpoints:
    - local vLLM Qwen3
    - OpenAI (BYOK)
    - Gemini (pending)
- When `raw`:
  - Uses existing in-house OpenAI-compatible client (single-shot).

## Key configuration

### Chat (local / default)
- `assistant.chat.llm.base-url` (default `http://127.0.0.1:8000`)
- `assistant.chat.llm.model` (default `Qwen/Qwen3-8B`)
- `assistant.chat.llm.api-key` (optional)

### Provider defaults
- OpenAI:
  - `assistant.chat.providers.openai.base-url` (default `https://api.openai.com`)
  - `assistant.chat.providers.openai.default-model` (default `gpt-4o-mini`)
- Gemini:
  - `assistant.chat.providers.gemini.base-url` (empty by default)
  - `assistant.chat.providers.gemini.default-model` (default `gemini-1.5-flash`)

## What’s next (todo)

### P0 — Make tool calling fully model-driven (Spring AI native)
1. Expose existing backend tools as Spring AI tools (`ToolCallback` / `@Tool`).
2. Let the model decide when to call tools (replace heuristic planner).
3. Implement tool loop:
   - model → tool call(s) → execute server-side (audited) → feed tool responses back → continue generation
4. Add strict limits:
   - max tool calls per request
   - per-tool timeouts
   - output size caps
   - allowlist by role (user/admin)

### P1 — Expand tool set for real “analysis” workflows
Prioritize:
- publish failure diagnosis (`getRecentPublishFailures`, `diagnosePublish`)
- device health report (`diagnoseDevice`, `summarizeSystemHealth`)
- error-code to docs mapping (`getErrorCodeHelp`)

### Provider roadmap
- OpenAI BYOK: supported.
- Gemini BYOK: confirm approach:
  - Gemini API key vs Vertex AI service account (Spring AI modules differ).

