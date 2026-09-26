package com.parallax.assistant.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.assistant.config.AssistantProperties;
import com.parallax.assistant.tools.ParallaxToolRegistry;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs one assistant chat turn (SPEC §12). Tool execution is user-controlled
 * ({@code internalToolExecutionEnabled=false}): the model returns tool-call requests, this service
 * executes them via {@link ToolCallingManager} — recording each call — and loops until the model
 * answers. If no model is configured (blank API key) chat is refused with 503.
 */
@Service
public class ChatService {

    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int SUMMARY_LIMIT = 200;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ToolCallingManager toolCallingManager;
    private final ParallaxToolRegistry registry;
    private final ConversationStore store;
    private final AssistantProperties properties;
    private final SystemMessage systemMessage;
    private final ObjectMapper objectMapper;

    public ChatService(ObjectProvider<ChatModel> chatModelProvider, ToolCallingManager toolCallingManager,
                       ParallaxToolRegistry registry, ConversationStore store, AssistantProperties properties,
                       SystemMessage assistantSystemMessage, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.toolCallingManager = toolCallingManager;
        this.registry = registry;
        this.store = store;
        this.properties = properties;
        this.systemMessage = assistantSystemMessage;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return chatModelProvider.getIfAvailable() != null;
    }

    public ChatViews.ChatResponse chat(String conversationId, String userText) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new AssistantNotConfiguredException();
        }
        String id = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(store.history(id));
        UserMessage userMessage = new UserMessage(userText);
        messages.add(userMessage);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(properties.getAssistant().getModel())
                .maxTokens(properties.getAssistant().getMaxTokens())
                .toolCallbacks(List.of(registry.callbacks()))
                .internalToolExecutionEnabled(false)
                .build();

        ToolCallRecorder recorder = new ToolCallRecorder();
        Prompt prompt = new Prompt(messages, options);
        ChatResponse response = model.call(prompt);

        int rounds = 0;
        while (response.getResult().getOutput().hasToolCalls() && rounds++ < MAX_TOOL_ROUNDS) {
            AssistantMessage assistant = response.getResult().getOutput();
            ToolExecutionResult execution = toolCallingManager.executeToolCalls(prompt, response);
            record(recorder, assistant, execution);
            prompt = new Prompt(execution.conversationHistory(), options);
            response = model.call(prompt);
        }

        String answer = response.getResult().getOutput().getText();
        store.append(id, userMessage, new AssistantMessage(answer == null ? "" : answer));
        return new ChatViews.ChatResponse(id, answer, recorder.calls());
    }

    private void record(ToolCallRecorder recorder, AssistantMessage assistant, ToolExecutionResult execution) {
        Map<String, String> responseById = new HashMap<>();
        for (Message message : execution.conversationHistory()) {
            if (message instanceof ToolResponseMessage toolMessage) {
                for (ToolResponseMessage.ToolResponse toolResponse : toolMessage.getResponses()) {
                    responseById.put(toolResponse.id(), toolResponse.responseData());
                }
            }
        }
        for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
            recorder.record(call.name(), parseArguments(call.arguments()),
                    oneLine(responseById.get(call.id())), null);
        }
    }

    private Object parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, JsonNode.class);
        } catch (Exception e) {
            return arguments;
        }
    }

    private static String oneLine(String responseData) {
        if (responseData == null) {
            return null;
        }
        String flat = responseData.replaceAll("\\s+", " ").trim();
        return flat.length() > SUMMARY_LIMIT ? flat.substring(0, SUMMARY_LIMIT) + "…" : flat;
    }
}
