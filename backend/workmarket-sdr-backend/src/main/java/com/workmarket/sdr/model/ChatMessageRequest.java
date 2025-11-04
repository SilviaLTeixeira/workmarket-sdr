package com.workmarket.sdr.model;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private String sessionId;
    private String message;
}
