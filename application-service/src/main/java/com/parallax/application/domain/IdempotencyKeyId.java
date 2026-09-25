package com.parallax.application.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link IdempotencyKeyEntity} (client_id, idem_key) — SPEC §7, §14. */
public class IdempotencyKeyId implements Serializable {

    private String clientId;
    private String idemKey;

    public IdempotencyKeyId() {
    }

    public IdempotencyKeyId(String clientId, String idemKey) {
        this.clientId = clientId;
        this.idemKey = idemKey;
    }

    public String getClientId() {
        return clientId;
    }

    public String getIdemKey() {
        return idemKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyKeyId that)) {
            return false;
        }
        return Objects.equals(clientId, that.clientId) && Objects.equals(idemKey, that.idemKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, idemKey);
    }
}
