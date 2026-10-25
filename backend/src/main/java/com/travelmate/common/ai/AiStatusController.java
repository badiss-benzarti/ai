package com.travelmate.common.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the frontend show whether the AI is available. */
@RestController
@RequiredArgsConstructor
public class AiStatusController {

    private final OllamaClient ollama;

    @GetMapping("/api/ai/status")
    public AiStatus status() {
        return new AiStatus(ollama.isAvailable(), ollama.getModel());
    }

    public record AiStatus(boolean available, String model) {}
}
