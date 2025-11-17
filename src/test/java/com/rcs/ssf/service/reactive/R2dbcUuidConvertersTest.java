package com.rcs.ssf.service.reactive;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class R2dbcUuidConvertersTest {

    @Test
    void writingConverterConvertsUuidToString() {
        UUID id = UUID.randomUUID();

        String converted = R2dbcUuidConverters.UuidToStringConverter.INSTANCE.convert(
            Objects.requireNonNull(id));

        assertEquals(id.toString(), converted, "UUID should serialize to its canonical string");
    }

    @Test
    void readingConverterConvertsStringToUuid() {
        UUID id = UUID.randomUUID();

        UUID converted = R2dbcUuidConverters.StringToUuidConverter.INSTANCE.convert(
            Objects.requireNonNull(id.toString()));

        assertEquals(id, converted, "String should deserialize back to UUID");
    }
}
