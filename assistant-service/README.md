# assistant-service (port 8083)

A read-only Spring AI agent (SPEC §12). It answers underwriter and strategist questions by calling
Parallax's own REST APIs as tools. It is **read-only by credentials, not by prompt**: it authenticates
to application-service as the `ASSISTANT` role, which permits only a handful of GET endpoints, and it
has no write tool at all.

## Running

- `ANTHROPIC_API_KEY` — the Anthropic key. **Blank by default**: the service starts without it,
  `GET /api/v1/assistant/status` reports `configured: false`, and `POST /api/v1/assistant/chat`
  returns `503 { "title": "Assistant model not configured" }`.
- `PARALLAX_ASSISTANT_MODEL` — the Claude model id (default `claude-sonnet-5`).
- `PARALLAX_APPLICATION_URL` — application-service base URL (default `http://localhost:8080`).
- `ASSISTANT_USERNAME` / `ASSISTANT_PASSWORD` — the assistant's ASSISTANT credentials
  (defaults `assistant@parallax.dev` / `assistant-dev`).
- The assistant endpoints require one of the four INTERNAL demo users (password `demo-password`).

## Endpoints

- `GET /api/v1/assistant/status` → `{configured, provider, model}`
- `GET /api/v1/assistant/tools` → the seven tools `[{name, description, access}]`
- `POST /api/v1/assistant/chat {conversationId?, message}` → `{conversationId, answer, toolCalls}`

## Switching the provider to OpenAI

Anthropic is the default provider. To use OpenAI instead:

1. Replace the Anthropic starter dependency in `pom.xml` with the OpenAI starter (do **not** add both):

   ```xml
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-model-openai</artifactId>
   </dependency>
   ```

2. Swap the model bean in `AssistantConfig` to build an `OpenAiChatModel`
   (`OpenAiApi.builder().apiKey(...)`, `OpenAiChatOptions.builder().model(...)`), and exclude
   `OpenAiChatAutoConfiguration` instead of `AnthropicChatAutoConfiguration` in
   `AssistantServiceApplication` so the model is still built only when a key is present.

3. Set the properties:

   ```yaml
   spring:
     ai:
       openai:
         api-key: ${OPENAI_API_KEY:}
   parallax:
     assistant:
       model: gpt-4o        # a current OpenAI chat model id
   ```

The tools, tool loop, conversation memory, security and endpoints are provider-agnostic — only the
model bean and its properties change.
