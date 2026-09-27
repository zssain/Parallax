package com.parallax.assistant.chat;

import com.parallax.assistant.config.AssistantProperties;
import com.parallax.assistant.tools.ParallaxToolRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The assistant endpoints (SPEC §15): status, tools panel and chat. All require an INTERNAL user. */
@RestController
public class AssistantController {

    private final ChatService chatService;
    private final ParallaxToolRegistry registry;
    private final AssistantProperties properties;

    public AssistantController(ChatService chatService, ParallaxToolRegistry registry,
                               AssistantProperties properties) {
        this.chatService = chatService;
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/api/v1/assistant/status")
    public ChatViews.Status status() {
        return new ChatViews.Status(chatService.configured(), "openai", properties.getAssistant().getModel());
    }

    @GetMapping("/api/v1/assistant/tools")
    public List<ParallaxToolRegistry.ToolView> tools() {
        return registry.tools();
    }

    @PostMapping("/api/v1/assistant/chat")
    public ChatViews.ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.conversationId(), request.message());
    }
}
