package com.workmarket.sdr.controller;

import com.workmarket.sdr.model.ChatMessageRequest;
import com.workmarket.sdr.model.ChatMessageResponse;
import com.workmarket.sdr.model.Lead;
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

    public ChatController(PipefyService pipefyService) {
        this.pipefyService = pipefyService;
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> chat(@RequestBody ChatMessageRequest request) {

        Lead lead = new Lead();
        lead.setNome("Lead de Teste");
        lead.setEmail("teste@example.com");
        lead.setEmpresa("Empresa Teste");
        lead.setNecessidade("Necessidade ainda não informada");
        lead.setInteresseConfirmado(false);

        pipefyService.saveOrUpdateLead(lead);

        String reply = """
            Olá! Eu sou o assistente da WorkMarket.

            Recebi sua mensagem: "%s"

            Já estou simulando o registro do lead no Pipefy (ver logs).
            Em breve vou falar com a IA para te ajudar de verdade. 🙂
            """.formatted(request.getMessage());

        ChatMessageResponse response =
                new ChatMessageResponse(reply, false, null);

        return ResponseEntity.ok(response);
    }
}
