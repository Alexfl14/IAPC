package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
public class AiController {

    private final ChatClient chatClient;

    public AiController(@Qualifier("ollamaGeneralChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/ask")
    public String askAi(@RequestBody String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
