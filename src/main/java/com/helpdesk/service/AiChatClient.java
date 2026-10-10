package com.helpdesk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helpdesk.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Thin client for any OpenAI-compatible chat-completions API (Groq, Gemini's OpenAI
 * endpoint, Mistral, Cerebras, OpenRouter, ...), used by the chatbot for general
 * conversation. Disabled automatically when no API key is configured; any failure
 * returns Optional.empty() so callers can fall back to the knowledge-base-only
 * behaviour. The key comes from the CHATBOT_API_KEY environment variable and is
 * never logged.
 */
@Slf4j
@Component
public class AiChatClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int maxTokens;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AiChatClient(@Value("${app.chatbot.ai.api-key:}") String apiKey,
                         @Value("${app.chatbot.ai.model:openai/gpt-oss-20b}") String model,
                         @Value("${app.chatbot.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                         @Value("${app.chatbot.ai.max-tokens:1200}") int maxTokens) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.maxTokens = maxTokens;
        if (this.apiKey.isEmpty()) {
            log.info("Chatbot AI is disabled (no CHATBOT_API_KEY set) - using knowledge-base answers only.");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    /** messages must alternate user/assistant and end with a user turn. */
    public Optional<String> complete(String systemPrompt, List<ChatTurn> messages) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            if (model.contains("gpt-oss")) {
                // reasoning models spend tokens "thinking"; keep it short so the reply is not cut off
                body.put("reasoning_effort", "low");
            }
            ArrayNode arr = body.putArray("messages");
            arr.addObject().put("role", "system").put("content", systemPrompt);
            for (ChatTurn t : messages) {
                arr.addObject().put("role", t.role()).put("content", t.content());
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Chatbot AI call failed: HTTP {} - {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            String result = mapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText("").trim();
            return result.isEmpty() ? Optional.empty() : Optional.of(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Chatbot AI call failed: {}", e.toString());
            return Optional.empty();
        }
    }
}
