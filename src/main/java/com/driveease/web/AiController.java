package com.driveease.web;

import com.driveease.ai.AiAssistantService;
import com.driveease.dto.Dtos.ChatRequest;
import com.driveease.dto.Dtos.ChatResponse;
import com.driveease.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.driveease.web.AuthController.TOKEN_HEADER;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService assistant;
    private final AuthService authService;

    public AiController(AiAssistantService assistant, AuthService authService) {
        this.assistant = assistant;
        this.authService = authService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                             @RequestBody ChatRequest req) {
        return assistant.chat(req, authService.optionalCustomer(token));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("mode", assistant.mode());
    }
}
