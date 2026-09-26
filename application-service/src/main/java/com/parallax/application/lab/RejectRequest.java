package com.parallax.application.lab;

/** Body of {@code POST /api/v1/lab/versions/{v}/reject} (SPEC §15): the approver's rejection note. */
public record RejectRequest(String note) {
}
