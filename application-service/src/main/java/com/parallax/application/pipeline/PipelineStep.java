package com.parallax.application.pipeline;

/** Pipeline steps with the exact labels from SPEC §3. ENGINE's label gets the live version appended. */
public enum PipelineStep {
    VALIDATE("Validate request"),
    IDEMPOTENCY("Idempotency check"),
    BUREAU("Credit bureau · SOAP pull"),
    FRAUD_SCREEN("Fraud & identity screen"),
    ENGINE("Decision engine"),
    LEDGER_COMMIT("Ledger commit · single transaction");

    private final String label;

    PipelineStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
