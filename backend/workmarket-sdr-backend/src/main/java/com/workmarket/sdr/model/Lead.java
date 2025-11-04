package com.workmarket.sdr.model;

import lombok.Data;

@Data
public class Lead {

    private String nome;
    private String email;
    private String empresa;
    private String necessidade;
    private boolean interesseConfirmado;
    private String meetingLink;
    private String meetingDatetime;
}
