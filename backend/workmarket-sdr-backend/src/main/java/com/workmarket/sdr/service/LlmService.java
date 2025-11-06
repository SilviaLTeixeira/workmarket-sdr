package com.workmarket.sdr.service;

import com.workmarket.sdr.model.ChatTurn;
import com.workmarket.sdr.model.ChatTurn.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiUrl;
    private final String model;

    private final ConversationMemoryService memoryService;

    public LlmService(
            @Value("${llm.api.url}") String apiUrl,
            @Value("${llm.model}") String model,
            ConversationMemoryService memoryService
    ) {
        this.apiUrl = apiUrl;
        this.model = model;
        this.memoryService = memoryService;
    }

    public String generateReply(String sessionId, String userMessage) {

        // 1) Adiciona a fala do usuário na memória
        memoryService.addUserMessage(sessionId, userMessage);

        // 2) Busca histórico e vê quantas vezes o assistente já respondeu
        List<ChatTurn> history = memoryService.getHistory(sessionId);

        long assistantTurns = history.stream()
                .filter(turn -> turn.getRole() == Role.ASSISTANT)
                .count();

        String stage;
        if (assistantTurns == 0) {
            stage = "SITUACAO_PROBLEMA";
        } else if (assistantTurns == 1) {
            stage = "PROBLEMA_IMPLICACAO";
        } else if (assistantTurns == 2) {
            stage = "IMPLICACAO_NECESSIDADE";
        } else {
            stage = "NECESSIDADE_FECHAMENTO";
        }

        // 3) Regras gerais (sempre válidas)
        String baseRules = """
        Você é um SDR da WorkMarket, uma plataforma de mão de obra temporária
        para mercados e redes de varejo (supermercados, atacarejos, mercearias, redes de lojas etc).
        
        REGRAS GERAIS (IMPORTANTES):
        - Você NÃO é consultor de gestão de pessoas, RH ou logística.
        - NÃO dê conselhos sobre escala, organização de turnos ou gestão de equipe.
        - NÃO pergunte sobre faturamento, volume de vendas, ticket médio ou números financeiros.
        - NÃO pergunte sobre tipo de produto, mix de produtos ou categorias de mercadorias.
        - É ESTRITAMENTE PROIBIDO perguntar sobre:
          * "como funciona o processo de reposição",
          * "qual o volume de reposição",
          * "volume de reposições",
          * qualquer pergunta parecida que foque na QUANTIDADE de produtos ou fluxo de mercadoria.
          Em vez disso, sempre pergunte apenas sobre QUANTIDADE DE PESSOAS (quantos funcionários) e HORÁRIOS.
        
        - Se o cliente mencionar claramente uma função (ex: repositor, caixa, empacotador),
          assuma essa função como foco e não volte a perguntar que função é.
        
        Seu papel é SOMENTE:
        - entender quantas pessoas são necessárias, em quais dias/horários;
        - entender como a falta de pessoas atrapalha a operação;
        - mostrar como a WorkMarket pode fornecer profissionais temporários;
        - verificar se o cliente quer seguir para uma conversa/reunião.
        
        Use as mensagens anteriores como verdade. NÃO repita perguntas sobre coisas que o cliente já informou.
        
        Sempre responda em português do Brasil, em tom profissional, direto e empático.
        """;


        // 4) Prompt específico por fase (SPIN simplificado)
        String systemPrompt;

        switch (stage) {
            case "SITUACAO_PROBLEMA" -> systemPrompt = baseRules + """
            FASE ATUAL: SITUAÇÃO + PROBLEMA.
            
              Use o que o cliente já escreveu para entender o contexto
              (tipo de loja, quantidade de lojas, dias e horários críticos).
              NÃO repita informações que ele já informou.
    
              Objetivo desta fase:
              - reconhecer a dor de falta de pessoal;
              - fazer NO MÁXIMO 2 perguntas novas, focando em:
                * onde e quando falta gente (dias/horários/turnos);
                * quantas pessoas ele costuma precisar;
                * como ele resolve hoje (hora extra, improviso, fechar setor).
    
              NÃO pergunte:
              - tipo de produto que ele vende;
              - mix de mercadorias;
              - categorias de produtos;
              - processo de reposição;
              - volume de reposição;
              - faturamento, vendas ou dados financeiros;
              - detalhes de logística, estoque ou fluxo de mercadoria.
    
              Não dê dicas de gestão de equipe, organização de escala ou melhorias de processo interno.
    
              Só pergunte quais funções estão sendo afetadas (repositor, caixa, depósito, etc.)
              SE isso ainda não tiver ficado claro nas mensagens anteriores.
    
              Mantenha o foco em entender a necessidade de pessoal temporário
              e no impacto operacional causado pela falta de mão de obra.
            
            
            """;


            case "PROBLEMA_IMPLICACAO" -> systemPrompt = baseRules + """
            FASE ATUAL: PROBLEMA + IMPLICAÇÃO.
            
            Você JÁ TEM informações básicas de situação. NÃO volte a perguntar o que já foi dito.
            
            Use o histórico para:
            - aprofundar o problema (como a falta de gente atrapalha no dia a dia);
            - explorar IMPLICAÇÕES sem falar de dinheiro, apenas:
              * impacto no atendimento ao cliente;
              * sobrecarga da equipe;
              * necessidade de fechar setores, atrasar reposição etc.
            
            Faça no máximo 2 perguntas novas, SEMPRE focadas em:
            - horários/dias em que falta gente;
            - quantas pessoas seriam necessárias;
            - como o cliente está se virando hoje (hora extra, remanejamento, improviso).
            
            NÃO pergunte:
            - como funciona o processo de reposição;
            - qual o volume de reposição;
            - volume de reposições;
            - detalhes de logística, estoque ou fluxo de mercadorias.
            
            Não dê dicas de gestão de equipe ou de organização de processos internos.
            Mantenha o foco na necessidade de mão de obra temporária.
            """;

            case "IMPLICACAO_NECESSIDADE" -> systemPrompt = baseRules + """
            FASE ATUAL: IMPLICAÇÃO + NECESSIDADE DE SOLUÇÃO.
            
            A partir das respostas anteriores, mostre que você entendeu o impacto da falta de pessoas
            (atendimento pior, equipe cansada, dificuldade de cobrir escala).
            
            Agora:
            - NÃO fique fazendo mais perguntas de diagnóstico repetidas;
            - comece a conectar o problema com a necessidade de uma solução FLEXÍVEL de mão de obra
              (profissionais temporários/freelas para cobrir fins de semana, picos, feriados etc.);
            - pergunte se faz sentido para o cliente considerar uma solução assim.
            
            Faça no máximo 1 ou 2 perguntas focadas em:
            - "faz sentido pra você?"
            - "você gostaria de entender melhor como funcionaria essa solução de mão de obra temporária?"
            """;

            default -> systemPrompt = baseRules + """
            FASE ATUAL: NECESSIDADE + FECHAMENTO / PRÓXIMO PASSO.
            
            Você já entendeu o contexto, problema e impacto. NÃO volte para perguntas básicas.
            
            Agora você deve:
            - verificar explicitamente se o cliente tem INTERESSE em seguir com a solução;
            - se sim, pedir dados de contato (nome, e-mail, empresa) de forma natural;
            - encaminhar para o próximo passo (marcar conversa/reunião com o time comercial).
            
            Não faça mais diagnóstico de problema.
            Foque em:
            - confirmar interesse;
            - coletar dados básicos do lead;
            - sugerir claramente o próximo passo.
            """;
        }

        // 5) Monta a lista de mensagens: system + histórico
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatTurn turn : history) {
            String role;
            if (turn.getRole() == Role.USER) {
                role = "user";
            } else if (turn.getRole() == Role.ASSISTANT) {
                role = "assistant";
            } else {
                role = "system";
            }
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

        try {
            ResponseEntity<Map> responseEntity =
                    restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, Map.class);

            Map responseBody = responseEntity.getBody();
            if (responseBody == null) {
                log.error("Resposta vazia do LLM (Ollama)");
                return "Tive um problema para falar com o modelo de IA agora. Pode tentar de novo em alguns instantes?";
            }

            Map<String, Object> message = (Map<String, Object>) responseBody.get("message");
            if (message == null) {
                log.error("Resposta sem campo 'message': {}", responseBody);
                return "Não consegui entender a resposta do modelo de IA. Pode tentar reformular sua pergunta?";
            }

            Object contentObj = message.get("content");
            if (contentObj == null) {
                log.error("Campo 'content' nulo em 'message': {}", message);
                return "O modelo não retornou um texto desta vez. Pode tentar novamente?";
            }

            String aiReply = contentObj.toString();

            // 6) Guarda a resposta do assistente na memória
            memoryService.addAssistantMessage(sessionId, aiReply);

            return aiReply;

        } catch (Exception e) {
            log.error("Erro ao chamar o LLM (Ollama)", e);
            return "Tive um erro técnico ao falar com o modelo de IA agora. Pode tentar novamente em alguns instantes?";
        }
    }

}
