package com.workmarket.sdr.controller;

import com.workmarket.sdr.model.ChatMessageRequest;
import com.workmarket.sdr.model.ChatMessageResponse;
import com.workmarket.sdr.model.Lead;
import com.workmarket.sdr.service.LlmService;
import com.workmarket.sdr.service.PipefyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final PipefyService pipefyService;
    private final LlmService llmService;

    public ChatController(PipefyService pipefyService, LlmService llmService) {
        this.pipefyService = pipefyService;
        this.llmService = llmService;
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> chat(@RequestBody ChatMessageRequest request) {

        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        // 1) Resposta da IA local (Ollama)
        String aiReply = llmService.generateReply(sessionId, userMessage);

        // 2) Lead ainda de exemplo – depois vamos popular de verdade
        Lead lead = new Lead();
        lead.setNome("Lead de Teste");
        lead.setEmail("teste@example.com");
        lead.setEmpresa("Empresa Teste");
        lead.setNecessidade("Necessidade ainda não informada");
        lead.setInteresseConfirmado(false);

        pipefyService.saveOrUpdateLead(lead);

        // 3) Devolve pro frontend
        ChatMessageResponse response =
                new ChatMessageResponse(aiReply, false, null);

        return ResponseEntity.ok(response);
    }
}
