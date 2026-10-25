package com.travelmate.common.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Ollama client for every TravelMate module.
 *
 * Usage:
 *   String text = ollama.chat(systemPrompt, userPrompt);
 *   MyDto dto  = ollama.chatJson(systemPrompt, userPrompt, jsonSchema, MyDto.class);
 *
 * Throws {@link OllamaException} when Ollama is down or answers garbage,
 * so callers can fall back to a non-AI behaviour.
 */
@Slf4j
@Component
public class OllamaClient {

    /** How long Ollama keeps the model in memory after the last request. */
    private static final String KEEP_ALIVE = "30m";
    /** A 5-day itinerary is about 1500 tokens of JSON. */
    private static final int MAX_ANSWER_TOKENS = 3000;

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String model;

    public OllamaClient(JsonMapper jsonMapper,
                        @Value("${ollama.base-url}") String baseUrl,
                        @Value("${ollama.model}") String model,
                        @Value("${ollama.timeout-seconds}") int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.jsonMapper = jsonMapper;
        this.model = model;
    }

    public String getModel() {
        return model;
    }

    /** True when Ollama is running and the configured model is downloaded. */
    public boolean isAvailable() {
        try {
            TagsResponse tags = restClient.get().uri("/api/tags").retrieve().body(TagsResponse.class);
            return tags != null && tags.models() != null
                    && tags.models().stream().anyMatch(m -> m.name().equals(model) || m.name().equals(model + ":latest"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the model in memory in the background when the app starts,
     * so the first user request does not wait for the cold load.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.ofVirtual().start(() -> {
            if (!isAvailable()) return;
            try {
                restClient.post().uri("/api/generate")
                        .body(Map.of("model", model, "keep_alive", KEEP_ALIVE, "options", Map.of("num_ctx", 8192)))
                        .retrieve().toBodilessEntity();
                log.info("Ollama model {} loaded", model);
            } catch (Exception e) {
                log.warn("Ollama warm-up failed: {}", e.getMessage());
            }
        });
    }

    /** Free-text answer. */
    public String chat(String systemPrompt, String userPrompt) {
        return send(systemPrompt, userPrompt, null);
    }

    /** Answer forced to match {@code jsonSchema}, then parsed into {@code type}. */
    public <T> T chatJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema, Class<T> type) {
        String json = send(systemPrompt, userPrompt, jsonSchema);
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new OllamaException("Ollama returned invalid JSON: " + json, e);
        }
    }

    private String send(String systemPrompt, String userPrompt, Map<String, Object> format) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("keep_alive", KEEP_ALIVE);
        // 8k context leaves room for bigger catalogs once the group's real data is plugged in.
        // num_predict stops a runaway answer (small models sometimes loop on JSON output).
        body.put("options", Map.of("temperature", 0.3, "num_ctx", 8192, "num_predict", MAX_ANSWER_TOKENS));
        if (format != null) {
            body.put("format", format);
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        try {
            ChatResponse response = restClient.post().uri("/api/chat").body(body)
                    .retrieve().body(ChatResponse.class);
            if (response == null || response.message() == null || response.message().content() == null) {
                throw new OllamaException("Empty response from Ollama", null);
            }
            String content = response.message().content();
            if ("length".equals(response.doneReason())) {
                throw new OllamaException("Ollama answer cut after " + MAX_ANSWER_TOKENS + " tokens, ending with: "
                        + content.substring(Math.max(0, content.length() - 300)), null);
            }
            return content;
        } catch (OllamaException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(Message message, @JsonProperty("done_reason") String doneReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagsResponse(List<ModelInfo> models) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfo(String name) {}
}
