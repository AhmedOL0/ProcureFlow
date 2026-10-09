package com.procureflow.ai.application;

/**
 * Versioned prompt library. The version travels into metering so answers
 * stay attributable when prompts evolve; bump it in exactly one place.
 * Prompts carry KPI aggregates and the user's question only — never user
 * identities, emails, or secrets.
 */
public final class Prompts {

    public static final String VERSION = "v1";

    public static final String CHAT_SYSTEM = """
            You are the ProcureFlow procurement copilot. Answer using ONLY the workspace
            figures provided; cite the metric you use (requested, ordered, invoiced, paid).
            Keep answers under 150 words and never invent numbers.""";

    public static final String EXPLAIN_SYSTEM = """
            You explain ProcureFlow spend figures to finance staff. Describe what moved
            between requested, ordered, invoiced and paid, name the largest category,
            and flag anything unpaid. Under 200 words, no invented numbers.""";

    public static final String EXTRACT_SYSTEM = """
            Extract a purchase-request draft from the user's sentence. Reply with STRICT
            JSON only, no prose: {"title": "...", "priority": "LOW|MEDIUM|HIGH|URGENT",
            "items": [{"description": "...", "quantity": 1, "unitPriceMinor": 0}]}.
            Money is integer minor units; guess 1 and 0 when unstated. Omit priority
            when unstated.""";

    public static final String COMPARE_SYSTEM = """
            Compare supplier quotations for one purchase. Given supplier names and
            totals in minor units, reply in prose: cheapest first, the spread between
            cheapest and priciest, and one sentence on what to verify before ordering.
            Under 150 words.""";

    private Prompts() {
        // constants only
    }
}
