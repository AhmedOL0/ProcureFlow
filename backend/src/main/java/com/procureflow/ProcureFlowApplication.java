package com.procureflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ProcureFlow backend entry point: an AI-powered multi-tenant B2B
 * procurement and supplier management API, built as a modular monolith.
 * Scheduling drives background backstops only (approval escalation);
 * every read and write path also escalates lazily.
 */
@SpringBootApplication
@EnableScheduling
public class ProcureFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcureFlowApplication.class, args);
    }
}
