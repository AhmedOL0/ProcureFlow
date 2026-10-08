package com.procureflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ProcureFlow backend entry point: an AI-powered multi-tenant B2B
 * procurement and supplier management API, built as a modular monolith.
 */
@SpringBootApplication
public class ProcureFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcureFlowApplication.class, args);
    }
}
