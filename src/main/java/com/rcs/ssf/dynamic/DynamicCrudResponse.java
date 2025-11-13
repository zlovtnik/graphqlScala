package com.rcs.ssf.dynamic;

import java.util.Optional;

public record DynamicCrudResponse(
        int affectedRows,
        String message,
        String generatedId
) {

    public Optional<String> optionalGeneratedId() {
        return Optional.ofNullable(generatedId);
    }
}
