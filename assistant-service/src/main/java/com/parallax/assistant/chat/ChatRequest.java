package com.parallax.assistant.chat;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/assistant/chat} (SPEC §15): an optional conversation id and the message. */
public record ChatRequest(String conversationId, @NotBlank String message) {
}
