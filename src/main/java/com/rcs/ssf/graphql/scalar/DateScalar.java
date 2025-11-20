package com.rcs.ssf.graphql.scalar;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Custom GraphQL scalar for Date type.
 * Handles serialization and deserialization of LocalDate objects to/from ISO 8601 format (yyyy-MM-dd).
 */
public class DateScalar {

    private static final Logger logger = LoggerFactory.getLogger(DateScalar.class);
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Creates a GraphQL Date scalar type.
     *
     * @return GraphQLScalarType for Date
     */
    public static GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .description("A Date scalar that accepts dates in ISO 8601 format (yyyy-MM-dd)")
                .coercing(new Coercing<LocalDate, String>() {

                    @Override
                    public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
                            throws CoercingSerializeException {
                        if (dataFetcherResult == null) {
                            return null;
                        }
                        if (dataFetcherResult instanceof LocalDate) {
                            LocalDate localDate = (LocalDate) dataFetcherResult;
                            return localDate.format(ISO_DATE_FORMATTER);
                        }
                        throw new CoercingSerializeException(
                                "Cannot serialize " + dataFetcherResult.getClass().getSimpleName()
                                        + " to a Date scalar. Expected LocalDate."
                        );
                    }

                    @Override
                    public LocalDate parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
                            throws CoercingParseValueException {
                        if (input == null) {
                            return null;
                        }
                        if (input instanceof String) {
                            return parseDateString((String) input);
                        }
                        if (input instanceof LocalDate) {
                            return (LocalDate) input;
                        }
                        throw new CoercingParseValueException(
                                "Cannot parse " + input.getClass().getSimpleName()
                                        + " to a Date scalar. Expected a String in ISO 8601 format (yyyy-MM-dd) or LocalDate."
                        );
                    }

                    @Override
                    public LocalDate parseLiteral(Value<?> input, CoercedVariables variables,
                            GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
                        if (!(input instanceof StringValue)) {
                            throw new CoercingParseLiteralException(
                                    "Cannot parse " + input.getClass().getSimpleName()
                                            + " to a Date scalar. Expected a String in ISO 8601 format (yyyy-MM-dd)."
                            );
                        }
                        try {
                            String value = ((StringValue) input).getValue();
                            return LocalDate.parse(value, ISO_DATE_FORMATTER);
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException(
                                    "Invalid date format: " + e.getMessage(), e
                            );
                        }
                    }

                    private LocalDate parseDateString(String dateString) throws CoercingParseValueException {
                        try {
                            return LocalDate.parse(dateString, ISO_DATE_FORMATTER);
                        } catch (DateTimeParseException e) {
                            logger.warn("Failed to parse date string: {}", dateString, e);
                            throw new CoercingParseValueException(
                                    "Invalid date format. Expected ISO 8601 format (yyyy-MM-dd): " + e.getMessage(), e
                            );
                        }
                    }
                })
                .build();
    }
}
