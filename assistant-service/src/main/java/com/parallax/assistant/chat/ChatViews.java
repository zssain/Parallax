package com.parallax.assistant.chat;

import java.util.List;

/** Response DTOs for the assistant endpoints (SPEC §15). */
public final class ChatViews {

    private ChatViews() {
    }

    public record Status(boolean configured, String provider, String model) {
    }

    public record ChatResponse(String conversationId, String answer, List<ToolCall> toolCalls) {
    }

    /** One recorded tool call for the UI chips: what was called, with what, a one-line summary, any error. */
    public record ToolCall(String name, Object arguments, String summary, String error) {
    }
}
