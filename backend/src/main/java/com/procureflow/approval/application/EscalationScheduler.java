package com.procureflow.approval.application;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly driver for approval escalation: overdue steps move to the next
 * level. Reads and decisions also escalate lazily, so this job is a
 * backstop, not the mechanism tests rely on.
 */
@Component
public class EscalationScheduler {

    private final ApprovalService approvals;

    public EscalationScheduler(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Scheduled(fixedDelay = 3600000)
    public void escalateOverdue() {
        approvals.escalateOverdue(Instant.now());
    }
}
