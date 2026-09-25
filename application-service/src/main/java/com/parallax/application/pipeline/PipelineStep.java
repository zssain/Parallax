package com.parallax.application.pipeline;

/** Pipeline steps with the exact labels from SPEC §3 (ENGINE and LEDGER_COMMIT arrive in Prompt 09). */
public enum PipelineStep {
    VALIDATE("Validate request"),
    IDEMPOTENCY("Idempotency check"),
    BUREAU("Credit bureau · SOAP pull"),
    FRAUD_SCREEN("Fraud & identity screen");

    private final String label;

    PipelineStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
