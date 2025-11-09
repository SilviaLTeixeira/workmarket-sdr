package com.workmarket.sdr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Lead {

    private String nome;
    private String email;
    private String empresa;
    private String necessidade;
    private boolean interesseConfirmado;
    private String meetingLink;
    private String meetingDatetime;
}
