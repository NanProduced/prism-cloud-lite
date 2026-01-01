# Prism Cloud Lite AI Assistant — Frontend Integration Guide

## 1) Endpoint

- Method: `POST`
- URL: `/api/chat`
- Response: `text/event-stream`
- Stream terminator: `data: [DONE]`

## 2) Auth

Backend uses gateway/core security based on `CLOUD_AUTH` header (user identity).

- Required header:
  - `CLOUD_AUTH: <token>`

If you already have an auth wrapper in frontend, ensure this header is included on `/api/chat` requests.

## 3) Request shape (Vercel AI SDK)

Frontend uses Vercel AI SDK (`useChat`) and sends a JSON payload with message history.

Backend currently only requires that the last user message text is present (it extracts via parser).

Minimum requirement:
```json
{
  "messages": [
    { "role": "user", "content": "我有多少台离线设备？" }
  ]
}
```

## 4) SSE response protocol (UIMessageChunk-compatible)

Each SSE event:
- is one `data:` line
- the `data:` value is either:
  - a JSON object (UIMessageChunk-like)
  - or the terminal string `[DONE]`

### 4.1 Text streaming

Backend emits:
1. `{"type":"start"}`
2. `{"type":"text-start","id":"text-1"}`
3. one or more `{"type":"text-delta","id":"text-1","delta":"..."}` chunks
4. `{"type":"text-end","id":"text-1"}`
5. `{"type":"finish","finishReason":"stop|length|tool-calls|error|other"}`
6. `[DONE]`

### 4.2 Tool call streaming

Backend may emit:
- `tool-input-available` (tool requested)
- `tool-output-available` (tool succeeded)
- `tool-output-error` (tool failed)

Example:
```json
{"type":"tool-input-available","toolCallId":"tool-xxx","toolName":"analyzeOfflineDevices","input":{"limit":50},"providerExecuted":true}
{"type":"tool-output-available","toolCallId":"tool-xxx","output":{"offlineCount":3,"items":[...]},"providerExecuted":true}
```

Notes:
- `providerExecuted=false` is reserved for “frontend-only” tools (e.g., navigation hint).
- `providerExecuted=true` means server executed the tool and returned output.

### 4.3 RAG sources

After text, backend may emit `source-url` entries:
```json
{"type":"source-url","sourceId":"help.readme/en","url":"/help/README","title":"Help Center"}
```

Frontend can render these as citations/links.

## 5) Frontend wiring (recommended)

### 5.1 Vercel AI SDK

Keep using your existing `useChat` + SSE handling.

Key requirement: request must set `Accept: text/event-stream` and include `CLOUD_AUTH`.

If you are using a custom `fetch` in `useChat`:
- add header `CLOUD_AUTH`
- preserve SSE streaming (do not buffer the whole response)

### 5.2 Abort / cancel

`SseEmitter` keeps the connection open; client abort should close the stream.

## 6) BYOK config UI (optional, recommended)

Backend provides user model config APIs:
- `GET /api/v1/assistant/model-configs`
- `POST /api/v1/assistant/model-configs`
- `POST /api/v1/assistant/model-configs/{provider}/default`
- `DELETE /api/v1/assistant/model-configs/{provider}`

Frontend can build a simple settings page:
- provider selector (local-vllm/openai/gemini)
- model input (optional)
- apiKey input (masked)
- enable/disable toggle
- set default action

Important:
- Storing API keys requires server master key configured (`PRISM_AI_CREDENTIALS_MASTER_KEY`).

## 7) Backend engine switch (for environments)

Backend supports:
- `ASSISTANT_CHAT_ENGINE=raw` (legacy client)
- `ASSISTANT_CHAT_ENGINE=spring-ai` (Spring AI streaming client)

Frontend does not need any change across these modes as long as `/api/chat` contract stays stable.

## 8) Error handling

Backend emits error as chunks then `[DONE]`:
```json
{"type":"error","errorText":"..."}
{"type":"finish","finishReason":"error"}
```

Frontend should:
- show error message
- mark the message as finished

