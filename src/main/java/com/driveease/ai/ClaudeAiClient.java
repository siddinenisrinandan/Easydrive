package com.driveease.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.driveease.dto.Dtos.ChatTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Optional Claude integration for the assistant.
 * Active only when ANTHROPIC_API_KEY is set; otherwise the free offline engine answers.
 * Any failure (no network, rate limit, refusal) returns empty so the caller falls back to the offline engine.
 */
@Component
public class ClaudeAiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiClient.class);

    private final String apiKey;
    private final String model;
    private volatile AnthropicClient client;

    public ClaudeAiClient(@Value("${app.ai.anthropic-api-key:}") String apiKey,
                          @Value("${app.ai.model:claude-opus-5-5}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String getModel() {
        return model;
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
            }
        }
        return client;
    }

    public Optional<String> ask(String systemPrompt, List<ChatTurn> history, String userMessage) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(systemPrompt)
                    // a chat assistant does not need deep reasoning: keep answers fast and cheap
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());

            for (ChatTurn t : normalize(history, userMessage)) {
                if ("assistant".equals(t.role())) {
                    params.addAssistantMessage(t.text());
                } else {
                    params.addUserMessage(t.text());
                }
            }

            Message response = client().messages().create(params.build());
            String stop = response.stopReason().map(Object::toString).orElse("");
            if ("refusal".equalsIgnoreCase(stop)) {
                log.info("Claude declined this request; using the offline assistant instead");
                return Optional.empty();
            }
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"))
                    .trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (RateLimitException e) {
            log.warn("Claude rate limit reached, using offline assistant");
        } catch (AnthropicServiceException e) {
            log.warn("Claude API error, using offline assistant: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Could not reach Claude ({}), using offline assistant", e.toString());
        }
        return Optional.empty();
    }

    /**
     * The Messages API needs turns that start with "user" and alternate roles.
     * Keeps the last 12 turns, drops leading assistant turns (the welcome message) and merges repeats.
     */
    static List<ChatTurn> normalize(List<ChatTurn> history, String userMessage) {
        List<ChatTurn> all = new ArrayList<>();
        if (history != null) {
            int from = Math.max(0, history.size() - 12);
            for (ChatTurn t : history.subList(from, history.size())) {
                if (t == null || t.text() == null || t.text().isBlank()) {
                    continue;
                }
                all.add(new ChatTurn("assistant".equals(t.role()) ? "assistant" : "user", t.text()));
            }
        }
        all.add(new ChatTurn("user", userMessage));

        List<ChatTurn> out = new ArrayList<>();
        for (ChatTurn t : all) {
            if (out.isEmpty() && "assistant".equals(t.role())) {
                continue;
            }
            if (!out.isEmpty() && out.get(out.size() - 1).role().equals(t.role())) {
                ChatTurn last = out.remove(out.size() - 1);
                out.add(new ChatTurn(last.role(), last.text() + "\n\n" + t.text()));
            } else {
                out.add(t);
            }
        }
        return out;
    }
}
