# Prism Cloud Lite AI Assistant (Backend) — Spring AI + Function Calling Plan

## Goals

1. Keep frontend contract stable: `POST /api/chat` returns `text/event-stream` where each `data:` is a JSON `UIMessageChunk`, and ends with `data: [DONE]` (Vercel AI SDK compatible).
2. Support multi-provider LLM routing with BYOK (bring your own key):
   - `local-vllm` (local Qwen3 via OpenAI-compatible endpoint)
   - `openai` (ChatGPT models)
   - `gemini` (planned; BYOK key compatibility depends on provider API shape)
3. Enable **function calling** so the assistant can query real business data (DB/use-cases), not only answer text.
4. Ship incrementally with safe rollback switches.

## Current Status (already implemented)

### RAG ingestion + retrieval
- Docs ingestion reads from `../docs/help` and `../docs/help-en` and writes to Postgres `assistant` schema (documents + chunks + embeddings).
- Chat retrieval can fetch topK chunks and inject into prompt.

### `/api/chat` SSE contract
- Endpoint: `POST /api/chat` (SSE)
- Emits `UIMessageChunk` JSON events, then `[DONE]`.
- Supports emitting tool call chunks:
  - `tool-input-available`
  - `tool-output-available`
  - `tool-output-error`

### BYOK (user model configs)
- Table: `assistant.user_ai_model_config`
- API:
  - `GET /api/v1/assistant/model-configs`
  - `POST /api/v1/assistant/model-configs`
  - `POST /api/v1/assistant/model-configs/{provider}/default`
  - `DELETE /api/v1/assistant/model-configs/{provider}`
- Encryption: AES-GCM with master key `PRISM_AI_CREDENTIALS_MASTER_KEY` (base64 32 bytes).

### Function/tool framework + audit
- Table: `assistant.assistant_tool_audit_log`
- Server-side tools exist (examples): `searchDevices`, `getDeviceDetail`, `analyzeOfflineDevices`
- Tools are executed server-side with:
  - strict input parsing
  - output shaping (limit fields, omit sensitive data)
  - audit logging

## Target Architecture

### High-level flow

1. Frontend sends `POST /api/chat` with AI SDK request JSON.
2. Backend extracts last user text (and later full message history).
3. Backend builds prompt:
   - system instructions
   - optional RAG context
4. Backend calls LLM (streaming):
   - Use **Spring AI** for provider clients and streaming.
   - Route provider/model/apiKey by user’s default config (BYOK).
5. If LLM requests tool calls:
   - Execute tools server-side via existing `AssistantToolExecutor` (audited).
   - Feed tool outputs back into the model (iterative tool loop).
6. Stream answer chunks as `UIMessageChunk` SSE events, end with `[DONE]`.

### Why Spring AI
- Unifies: prompt building, streaming, tool calling, and provider integrations.
- Provides tool calling manager + resolver so you don’t need to hand-roll JSON tool protocol.
- Makes adding new providers consistent (OpenAI/compatible, local endpoints, etc.).

## Provider Strategy (BYOK)

### Provider IDs
- `local-vllm`: local OpenAI-compatible endpoint (e.g., vLLM) running Qwen3.
- `openai`: OpenAI API (BYOK key).
- `gemini`: **TBD**
  - If using Google Vertex AI Gemini: usually service account flow (not BYOK API key).
  - If using Gemini API key + OpenAI-compatible endpoint: may be possible, but needs validation.

### Routing rules
1. If user has an enabled default provider config, use it.
2. Else fallback to `assistant.chat.llm.*` (current config → local default).

### Configuration
Keep existing defaults:
- `assistant.chat.llm.base-url`
- `assistant.chat.llm.model`
- `assistant.chat.llm.api-key` (optional)

Add a safe engine switch:
- `assistant.chat.engine=raw|spring-ai` (default `raw` initially, then flip to `spring-ai`).

## Function Calling (Tools)

### Principles
- **Server decides what data to expose**: tool outputs must be safe-by-default.
- **Always enforce authorization** by `userId` from `CLOUD_AUTH`.
- **Hard limits** on list sizes, time ranges, and returned fields.
- **Audit all tool calls**: tool name, input JSON, success, elapsed time.
- **No secrets** in logs: do not log raw API keys; tool input should be validated and minimized.

### P0 tools (should exist early)
These map directly to common operations and are low-risk:

1. `searchDevices(keyword?, limit?)`
2. `getDeviceDetail(deviceId)`
3. `analyzeOfflineDevices(limit?)`
4. `getProgramDetail(programId)` (if program entity exists and is user-scoped)
5. `getRecentPublishFailures(sinceMinutes, limit)` (needs publish logs/events)
6. `getErrorCodeHelp(errorCode)` (map platform error codes to docs)

### P1 tools (higher value)
1. `summarizeSystemHealth()` (compose counts: devices online/offline, recent publishes, alert count)
2. `diagnoseDevice(deviceId)` (rule-based: last heartbeat, last content sync, last publish, etc.)
3. `recommendNextActions(context)` (guided checklist + links to help docs)

## Implementation Plan (Incremental)

### Milestone M3 — Spring AI chat client + streaming (no tool calling yet)
1. Add Maven dependencies for Spring AI OpenAI + model + retry.
2. Implement `AssistantChatModelRouter`:
   - loads user default provider config
   - constructs a Spring AI model for that provider (baseUrl/model/apiKey)
3. Implement `SpringAiChatClient` to stream model output and map to AI SDK SSE chunks.
4. Add `assistant.chat.engine` switch to control rollout.

### Milestone M4 — Spring AI native tool calling
1. Wrap existing tools as Spring AI tools (via `@Tool` or tool callback registry).
2. Replace heuristic `AssistantToolPlanner`:
   - model chooses tools
   - backend executes tools (still using existing executor/audit)
3. Tool loop:
   - model → tool call(s) → tool output(s) → model continues
4. Add tool policy:
   - allowed tools per user role
   - max tool calls per request
   - timeouts

### Milestone M5 — Provider expansion (OpenAI + Gemini)
1. OpenAI BYOK: supported directly.
2. Gemini BYOK: validate best path:
   - Option A: Gemini API key via OpenAI-compatible endpoint (if available)
   - Option B: Vertex AI Gemini via service account (admin configured)

## Rollback / Safety
- Keep existing raw OpenAI-compatible client as fallback (`assistant.chat.engine=raw`).
- Keep `/api/chat` response format unchanged.
- Keep tool execution server-side and audited.

## Open Questions
1. Gemini provider: confirm desired deployment (API key vs Vertex AI).
2. Tool inventory: confirm top business use-cases to prioritize (publish diagnosis, device health, billing/quota, etc.).

