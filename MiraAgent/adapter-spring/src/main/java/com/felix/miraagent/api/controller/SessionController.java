package com.felix.miraagent.api.controller;

import com.felix.miraagent.api.dto.MessageDto;
import com.felix.miraagent.session.SessionStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionStore sessionStore;

    public SessionController(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable String sessionId) {
        List<MessageDto> messages = sessionStore.loadMessages(sessionId)
                .stream()
                .map(MessageDto::from)
                .toList();
        return ResponseEntity.ok(messages);
    }
}
