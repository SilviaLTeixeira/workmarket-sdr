package com.workmarket.sdr.service;

import com.workmarket.sdr.model.Lead;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PipefyService {

    public void saveOrUpdateLead(Lead lead) {
        log.info("Simulando envio de lead para o Pipefy: {}", lead);
    }
}
