package com.example.ssf.dynamic;

import java.util.Objects;

public record DynamicCrudAuditContext(
        String actor,
        String traceId,
        String clientIp,
        String metadata
) {
    public DynamicCrudAuditContext {
        Objects.requireNonNull(actor, "actor is required for audit trail");
    }
}
