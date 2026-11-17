package ek.osnb.demo.coffeeshop.assistant;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private final AssistantService service;

    AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    ChatResponse chat(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request) {

        return new ChatResponse(
                service.chat(authentication, request.message())
        );
    }
}