package com.workmarket.sdr.service;

import com.workmarket.sdr.model.ChatTurn;
import com.workmarket.sdr.model.Lead;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Service
public class ConversationMemoryService {

    private final ConcurrentMap<String, List<ChatTurn>> sessions = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Lead> leads = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, String> stages = new ConcurrentHashMap<>();

    public List<ChatTurn> getHistory(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new ArrayList<>());
    }

    public void addUserMessage(String sessionId, String content) {
        getHistory(sessionId).add(new ChatTurn(ChatTurn.Role.USER, content));
    }

    public void addAssistantMessage(String sessionId, String content) {
        getHistory(sessionId).add(new ChatTurn(ChatTurn.Role.ASSISTANT, content));
    }

    public List<ChatTurn> getLastNHistory(String sessionId, int n) {
        List<ChatTurn> history = getHistory(sessionId);
        return history.size() <= n
                ? new ArrayList<>(history)
                : new ArrayList<>(history.subList(history.size() - n, history.size()));
    }

    public void trimHistory(String sessionId, int maxMessages) {
        List<ChatTurn> history = getHistory(sessionId);
        if (history.size() > maxMessages) {
            int remove = history.size() - maxMessages;
            history.subList(0, remove).clear();
        }
    }

    public Lead getLead(String sessionId) {
        return leads.computeIfAbsent(sessionId, id -> new Lead());
    }

    public void setStage(String sessionId, String stage) {
        stages.put(sessionId, stage);
    }

    public String getStage(String sessionId) {
        return stages.getOrDefault(sessionId, "DIAGNOSTICO");
    }

    public void clear(String sessionId) {
        sessions.remove(sessionId);
        leads.remove(sessionId);
        stages.remove(sessionId);
    }
}