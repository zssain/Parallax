package com.parallax.assistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.assistant.client.ParallaxClient;
import com.parallax.assistant.config.AssistantProperties;
import com.parallax.assistant.tools.ParallaxTools;
import com.parallax.assistant.tools.ParallaxToolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A scripted ChatModel that issues two tool calls (getDecision, getReasonCodes) then answers. The
 * user-controlled tool loop executes both against a stubbed ParallaxClient and records them (SPEC §12).
 */
class ChatFlowTest {

    @Test
    void declineQuestionCallsGetDecisionThenGetReasonCodes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ParallaxClient client = Mockito.mock(ParallaxClient.class);
        Mockito.when(client.getApplication("APP-1041")).thenReturn(mapper.readTree("""
                {"applicationId":"APP-1041","displayName":"P•••• S•••••","product":"REWARDS_CARD","status":"DECIDED",
                 "address":"1 Main St","current":{"outcome":"DECLINED","creditLimit":0,"override":null},
                 "base":{"ruleVersion":"v1.3","score":445,
                         "reasonCodes":[{"code":"R22","description":"Delinquency","applicantFacing":true}],
                         "fraudFlags":[]},
                 "breakdown":{"approveCutoff":680,"referCutoff":620,"scoreParts":[{"code":"R22","pointsLost":130}]}}
                """));

        ParallaxToolRegistry registry = new ParallaxToolRegistry(new ParallaxTools(client));
        AssistantProperties properties = new AssistantProperties();
        properties.getAssistant().setModel("test-model");

        ChatModel scriptedModel = new ScriptedChatModel();
        ChatService service = new ChatService(single(scriptedModel), ToolCallingManager.builder().build(),
                registry, new ConversationStore(), properties, new SystemMessage("system"), mapper);

        ChatViews.ChatResponse response = service.chat(null, "Why was APP-1041 declined?");

        assertThat(response.answer()).contains("declined");
        assertThat(response.toolCalls()).extracting(ChatViews.ToolCall::name)
                .containsExactly("getDecision", "getReasonCodes");
        assertThat(response.conversationId()).isNotBlank();
    }

    /** Round 1 → two tool calls; round 2 → the final answer. */
    private static final class ScriptedChatModel implements ChatModel {
        private int round = 0;

        @Override
        public ChatResponse call(Prompt prompt) {
            if (round++ == 0) {
                AssistantMessage message = AssistantMessage.builder().toolCalls(List.of(
                        new AssistantMessage.ToolCall("call-1", "function", "getDecision",
                                "{\"applicationId\":\"APP-1041\"}"),
                        new AssistantMessage.ToolCall("call-2", "function", "getReasonCodes",
                                "{\"applicationId\":\"APP-1041\"}"))).build();
                return new ChatResponse(List.of(new Generation(message)));
            }
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("APP-1041 was declined for delinquency (R22)."))));
        }
    }

    /** A minimal ObjectProvider that always yields the given model. */
    private static ObjectProvider<ChatModel> single(ChatModel model) {
        return new ObjectProvider<>() {
            @Override
            public ChatModel getObject() {
                return model;
            }

            @Override
            public ChatModel getObject(Object... args) {
                return model;
            }

            @Override
            public ChatModel getIfAvailable() {
                return model;
            }

            @Override
            public ChatModel getIfUnique() {
                return model;
            }
        };
    }
}
