package com.procureflow.identity.application;

/**
 * Outbound-mail port. The identity module owns the contract; providers plug
 * in behind it (Brevo SMTP today, anything tomorrow). Sends are fire-and-
 * report: failures throw and the caller decides what the client may know.
 */
public interface MailPort {

    record OutgoingMail(String to, String subject, String textBody) {
    }

    void send(OutgoingMail mail);
}
