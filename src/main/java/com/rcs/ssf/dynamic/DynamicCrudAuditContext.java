package com.rcs.ssf.dynamic;

import java.util.Objects;

public record DynamicCrudAuditContext(
        String actor,
        String traceId,
        String clientIp,
        String metadata
) {
    public DynamicCrudAuditContext {
        Objects.requireNonNull(actor, "actor is required for audit trail");
        Objects.requireNonNull(traceId, "traceId is required for audit trail");

        actor = actor.trim();
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank for audit trail");
        }

        traceId = traceId.trim();
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank for audit trail");
        }

        if (clientIp != null) {
            clientIp = clientIp.trim();
        }

        if (metadata != null) {
            metadata = metadata.trim();
        }
    }
}
