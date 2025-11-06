package com.workmarket.sdr.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatTurn {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    private Role role;
    private String content;
}

