package com.workmarket.sdr.service;

import com.workmarket.sdr.model.ChatTurn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConversationMemoryService {

    private final ConcurrentMap<String, List<ChatTurn>> sessions = new ConcurrentHashMap<>();

    public List<ChatTurn> getHistory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new ArrayList<>());
    }

    public void addUserMessage(String sessionId, String content) {
        getHistory(sessionId).add(new ChatTurn(ChatTurn.Role.USER, content));
    }

    public void addAssistantMessage(String sessionId, String content) {
        getHistory(sessionId).add(new ChatTurn(ChatTurn.Role.ASSISTANT, content));
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
