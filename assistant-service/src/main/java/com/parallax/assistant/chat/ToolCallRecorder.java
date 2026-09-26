package com.parallax.assistant.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the tool calls made during a single chat turn (SPEC §12): each entry is the tool name, the
 * arguments the model supplied, a one-line summary of the result, and any error. Not thread-shared:
 * one recorder is created per chat request.
 */
public class ToolCallRecorder {

    private final List<ChatViews.ToolCall> calls = new ArrayList<>();

    public void record(String name, Object arguments, String summary, String error) {
        calls.add(new ChatViews.ToolCall(name, arguments, summary, error));
    }

    public List<ChatViews.ToolCall> calls() {
        return List.copyOf(calls);
    }
}
