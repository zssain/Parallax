package com.parallax.assistant.chat;

/** Thrown when a chat is attempted with no model configured (SPEC §12): rendered as 503. */
public class AssistantNotConfiguredException extends RuntimeException {

    public AssistantNotConfiguredException() {
        super("Assistant model not configured");
    }
}
