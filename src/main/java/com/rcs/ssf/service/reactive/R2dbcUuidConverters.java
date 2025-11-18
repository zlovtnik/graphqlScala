package com.rcs.ssf.service.reactive;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * Oracle's R2DBC driver does not natively handle {@link java.util.UUID}
 * bindings, so we
 * convert values to simpler JDBC-friendly types before they hit the wire. These
 * converters
 * are registered via
 * {@link org.springframework.data.r2dbc.convert.R2dbcCustomConversions}.
 */
final class R2dbcUuidConverters {

    private static final List<Converter<?, ?>> CONVERTERS = List.of(
            UuidToStringConverter.INSTANCE,
            StringToUuidConverter.INSTANCE);

    private R2dbcUuidConverters() {
    }

    static List<Converter<?, ?>> getConverters() {
        return CONVERTERS;
    }

    @WritingConverter
    enum UuidToStringConverter implements Converter<UUID, String> {
        INSTANCE;

        @Override
        public String convert(@NonNull UUID source) {
            return source.toString();
        }
    }

    @ReadingConverter
    enum StringToUuidConverter implements Converter<String, UUID> {
        INSTANCE;

        @Override
        public UUID convert(@NonNull String source) {
            return UUID.fromString(source);
        }
    }
}
