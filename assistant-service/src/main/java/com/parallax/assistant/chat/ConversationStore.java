package com.parallax.assistant.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory conversation memory (SPEC §12): the last 20 messages per conversation, at most 200
 * conversations (oldest evicted). Only user and assistant turns are stored — internal tool messages
 * are not part of the visible conversation.
 */
@Component
public class ConversationStore {

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_CONVERSATIONS = 200;

    private final Map<String, List<Message>> conversations = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    public List<Message> history(String conversationId) {
        synchronized (conversations) {
            return new ArrayList<>(conversations.getOrDefault(conversationId, List.of()));
        }
    }

    public void append(String conversationId, Message... messages) {
        synchronized (conversations) {
            List<Message> history = conversations.computeIfAbsent(conversationId, k -> new ArrayList<>());
            Collections.addAll(history, messages);
            while (history.size() > MAX_MESSAGES) {
                history.remove(0);
            }
        }
    }
}
