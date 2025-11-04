package com.workmarket.sdr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageResponse {
    private String reply;
    private boolean meetingScheduled;
    private String meetingLink;
}
