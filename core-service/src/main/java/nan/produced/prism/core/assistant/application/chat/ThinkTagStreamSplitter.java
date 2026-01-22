package nan.produced.prism.core.assistant.application.chat;

import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;

/**
 * Splits {@code <think>...</think>} spans into reasoning deltas while streaming.
 */
final class ThinkTagStreamSplitter {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final AiUiMessageSseWriter writer;
    private final StringBuilder buffer = new StringBuilder(1024);
    private Mode mode = Mode.TEXT;

    private enum Mode {TEXT, THINK}

    ThinkTagStreamSplitter(AiUiMessageSseWriter writer) {
        this.writer = writer;
    }

    void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        buffer.append(delta);
        process(false);
    }

    void flush() {
        process(true);
    }

    private void process(boolean flushAll) {
        while (true) {
            if (mode == Mode.TEXT) {
                int openIdx = buffer.indexOf(OPEN);
                if (openIdx >= 0) {
                    emitText(buffer.substring(0, openIdx));
                    buffer.delete(0, openIdx + OPEN.length());
                    mode = Mode.THINK;
                    continue;
                }

                int keep = flushAll ? 0 : keepSuffixThatMayStartTag(buffer, OPEN);
                if (keep > 0) {
                    int emitLen = buffer.length() - keep;
                    emitText(buffer.substring(0, emitLen));
                    buffer.delete(0, emitLen);
                } else {
                    emitText(buffer.toString());
                    buffer.setLength(0);
                }
                return;
            }

            int closeIdx = buffer.indexOf(CLOSE);
            if (closeIdx >= 0) {
                emitReasoning(buffer.substring(0, closeIdx));
                buffer.delete(0, closeIdx + CLOSE.length());
                mode = Mode.TEXT;
                continue;
            }

            int keep = flushAll ? 0 : keepSuffixThatMayStartTag(buffer, CLOSE);
            if (keep > 0) {
                int emitLen = buffer.length() - keep;
                emitReasoning(buffer.substring(0, emitLen));
                buffer.delete(0, emitLen);
            } else {
                emitReasoning(buffer.toString());
                buffer.setLength(0);
            }
            return;
        }
    }

    private static int keepSuffixThatMayStartTag(CharSequence buf, String tag) {
        int max = Math.min(buf.length(), tag.length() - 1);
        for (int keep = max; keep >= 1; keep--) {
            boolean matches = true;
            for (int j = 0; j < keep; j++) {
                if (buf.charAt(buf.length() - keep + j) != tag.charAt(j)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return keep;
            }
        }
        return 0;
    }

    private void emitText(String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        writer.textDelta(s);
    }

    private void emitReasoning(String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        writer.reasoningDelta(s);
    }
}
