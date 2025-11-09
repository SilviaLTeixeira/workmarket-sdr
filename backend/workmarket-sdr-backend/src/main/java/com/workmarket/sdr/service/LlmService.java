package com.workmarket.sdr.service;

import com.workmarket.sdr.model.ChatMessageResponse;
import com.workmarket.sdr.model.ChatTurn;
import com.workmarket.sdr.model.ChatTurn.Role;
import com.workmarket.sdr.model.Lead;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmService {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String model;
    private final ConversationMemoryService memoryService;

    public LlmService(
            RestTemplate llmRestTemplate,
            @Value("${llm.api.url}") String apiUrl,
            @Value("${llm.model}") String model,
            ConversationMemoryService memoryService
    ) {
        log.info(">> LlmService inicializado (modelo = {})", model);
        this.restTemplate = llmRestTemplate;
        this.apiUrl = apiUrl;
        this.model = model;
        this.memoryService = memoryService;
    }

    public ChatMessageResponse generateReply(String sessionId, String userMessage) {
        memoryService.addUserMessage(sessionId, userMessage);
        Lead lead = memoryService.getLead(sessionId);

        extractLeadData(lead, userMessage);

        List<ChatTurn> history = memoryService.getHistory(sessionId);
        if (history.size() > 20) memoryService.trimHistory(sessionId, 20);

        long assistantTurns = history.stream()
                .filter(turn -> turn.getRole() == Role.ASSISTANT)
                .count();

        // 🔧 Regras base
        String baseRules = """
        Você é um SDR da WorkMarket — uma plataforma B2B que conecta empresas
        (mercados, atacarejos e redes varejistas) a profissionais temporários já verificados.
        
        O usuário é SEMPRE o contratante (gestor, dono, responsável pela loja).
        Seu papel é entender as necessidades de mão de obra temporária e explicar como a WorkMarket resolve isso.
        
        IMPORTANTE: No contexto da WorkMarket, as palavras "caixa", "repositor" e "empacotador"
        se referem a **funções profissionais**, não a objetos ou estruturas físicas.
        Portanto, "caixa" significa o funcionário do caixa (operador de caixa).
        
        REGRAS GERAIS:
        - Nunca fale como se o usuário fosse o trabalhador.
        - Fale apenas sobre contratações, equipe, horários e impacto da falta de pessoas.
        - NÃO gere respostas em formato JSON, markdown, ou com aspas escapadas.
        - Apenas devolva o texto puro da resposta, sem usar "reply" ou chaves {}.
        - Evite perguntas sem sentido, como “quantas sextas por semana”.
        - Quando o cliente disser um dia (ex: sexta-feira), entenda como frequência semanal normal.
        - Seja empático, direto e natural.
        """;

        String stage = defineStage(lead, assistantTurns);
        String systemPrompt = buildPromptForStage(stage, baseRules);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatTurn turn : history) {
            String role = switch (turn.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                default -> "system";
            };
            messages.add(Map.of("role", role, "content", turn.getContent()));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        boolean meetingScheduled = false;
        String meetingLink = null;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null)
                return buildJsonReply("O modelo não respondeu corretamente.", false, null);

            Map<String, Object> message = (Map<String, Object>) responseBody.get("message");
            if (message == null)
                return buildJsonReply("Erro: resposta inesperada do modelo.", false, null);

            String aiReply = sanitizeReply(String.valueOf(message.get("content")));
            memoryService.addAssistantMessage(sessionId, aiReply);

            if (lead.getEmail() != null && !lead.getEmail().isBlank()
                    && lead.getNome() != null && !lead.getNome().isBlank()
                    && lead.getEmpresa() != null && !lead.getEmpresa().isBlank()) {

                lead.setInteresseConfirmado(true);

                if (lead.getMeetingLink() == null) {
                    lead.setMeetingLink("https://meet.workmarket.ai/" + sessionId);
                    lead.setMeetingDatetime(LocalDateTime.now().toString());
                }

                memoryService.setStage(sessionId, "FINALIZADO");
                meetingScheduled = true;
                meetingLink = lead.getMeetingLink();

            } else if (detectInterest(userMessage)) {
                lead.setInteresseConfirmado(true);
                memoryService.setStage(sessionId, "FECHAMENTO");
            }

            return buildJsonReply(aiReply, meetingScheduled, meetingLink);

        } catch (Exception e) {
            log.error("Erro ao chamar modelo LLM", e);
            return buildJsonReply("Erro técnico ao gerar resposta. Tente novamente mais tarde.", false, null);
        }
    }

    private String sanitizeReply(String raw) {
        if (raw == null) return "";

        return raw
                .replaceAll("(?i)\\{.*?\\}", "")           // remove blocos entre chaves
                .replaceAll("(?i)reply|meetingScheduled|meetingLink", "")
                .replaceAll("\\\\n", "\n")                 // quebra de linha
                .replaceAll("\\\\\"", "\"")                // aspas duplas
                .replaceAll("[{}\\\\]", "")                // sobras
                .replaceAll(" +", " ")                     // espaços duplos
                .trim();
    }

    private String defineStage(Lead lead, long assistantTurns) {
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) return "FINALIZADO";
        if (lead.isInteresseConfirmado()) return "FECHAMENTO";
        return switch ((int) assistantTurns) {
            case 0 -> "DIAGNOSTICO";
            case 1 -> "APRESENTACAO";
            default -> "FECHAMENTO";
        };
    }

    private void extractLeadData(Lead lead, String userMessage) {
        String msg = userMessage.toLowerCase();

        if (msg.contains("@") && msg.contains(".")) {
            lead.setEmail(userMessage.trim());
        } else if (msg.matches(".*\\b(repositor|caixa|empacotador|temporário|freela|freelancer)\\b.*")) {
            lead.setNecessidade(userMessage.trim());
        } else if (msg.matches("(?i).*(meu nome|sou|chamo|nome é|aqui é).*")) {
            lead.setNome(userMessage.replaceAll("(?i)(meu nome|sou|chamo|nome é|aqui é)", "").trim());
        } else if (msg.matches("(?i).*(empresa|mercado|loja|supermercado|atacarejo).*")) {
            lead.setEmpresa(userMessage.trim());
        }
    }

    private boolean detectInterest(String userMessage) {
        return userMessage.toLowerCase()
                .matches(".*\\b(sim|vamos|quero|pode marcar|bora|topo|claro|ok|fechado|pode ser|interessado)\\b.*");
    }

    private String buildPromptForStage(String stage, String baseRules) {
        return switch (stage) {
            case "DIAGNOSTICO" -> baseRules + """
            FASE: DIAGNÓSTICO INICIAL.
            - Entenda o tipo de função que falta (ex: repositor, caixa, empacotador);
            - Pergunte em quais dias e horários há falta;
            - Não repita perguntas óbvias.
            - Faça no máximo 2 perguntas curtas.
            """;

            case "APRESENTACAO" -> baseRules + """
            FASE: APRESENTAÇÃO.
            Explique:
            "A WorkMarket é uma plataforma digital que conecta mercados e redes varejistas
            a profissionais temporários já verificados e prontos pra trabalhar.
            Você pode solicitar repositores, caixas ou empacotadores apenas para os dias e horários necessários,
            evitando falta de pessoal em picos, feriados e fins de semana."

            Depois pergunte de forma natural se faz sentido para o cliente.
            """;

            case "FECHAMENTO" -> baseRules + """
            FASE: FECHAMENTO.
            O cliente já demonstrou interesse.
            - Peça nome completo, nome da empresa e e-mail corporativo.
            - Quando o e-mail for informado, finalize com uma mensagem cordial.
            """;

            case "FINALIZADO" -> baseRules + """
            FASE: ENCERRAMENTO.
            O cliente já forneceu e-mail.
            Finalize com:
            "Perfeito! Nosso time vai entrar em contato pra te mostrar a plataforma e os próximos passos."
            """;

            default -> baseRules;
        };
    }

    private ChatMessageResponse buildJsonReply(String reply, boolean meetingScheduled, String meetingLink) {
        return new ChatMessageResponse(
                reply.replace("\"", "'").trim(),
                meetingScheduled,
                meetingLink
        );
    }
}





