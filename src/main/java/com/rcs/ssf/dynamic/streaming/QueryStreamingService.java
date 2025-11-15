package com.rcs.ssf.dynamic.streaming;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides server-side cursor streaming support so large Oracle queries do not exhaust heap memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryStreamingService {

    private final DataSource dataSource;
    private final QueryStreamingProperties properties;
    private final MeterRegistry meterRegistry;

    public <T> Stream<T> stream(String sql, List<Object> parameters, RowMapper<T> rowMapper, QueryStreamOptions options) {
        QueryStreamOptions effectiveOptions = normalize(options);
        int fetchSize = Math.min(Math.max(effectiveOptions.fetchSize(), properties.getFetchSize()), properties.getMaxFetchSize());

        Connection connection = DataSourceUtils.getConnection(Objects.requireNonNull(dataSource, "DataSource must not be null"));
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchDirection(ResultSet.FETCH_FORWARD);
            statement.setFetchSize(fetchSize);
            statement.setQueryTimeout(properties.getIdleTimeoutSeconds());

            if (parameters != null) {
                for (int i = 0; i < parameters.size(); i++) {
                    statement.setObject(i + 1, parameters.get(i));
                }
            }

            resultSet = statement.executeQuery();
        } catch (SQLException e) {
            JdbcUtils.closeResultSet(resultSet);
            JdbcUtils.closeStatement(statement);
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw new DataAccessResourceFailureException("Unable to open streaming cursor", e);
        }

        final PreparedStatement streamingStatement = statement;
        final ResultSet streamingResultSet = resultSet;

        Counter rowCounter = meterRegistry.counter("query.stream.rows", "stream", effectiveOptions.streamName());
        ResultSetIterator<T> iterator = new ResultSetIterator<>(streamingResultSet, rowMapper, rowCounter);
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
        Stream<T> stream = StreamSupport.stream(spliterator, false);

        Timer timer = meterRegistry.timer("query.stream.duration", "stream", effectiveOptions.streamName());
        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicBoolean closed = new AtomicBoolean(false);
        return stream.onClose(() -> {
            if (closed.compareAndSet(false, true)) {
                sample.stop(timer);
                closeQuietly(streamingResultSet, streamingStatement, connection);
                if (log.isDebugEnabled()) {
                    log.debug("Closed streaming cursor '{}' after {} rows", effectiveOptions.streamName(),
                            String.format(Locale.ROOT, "%d", iterator.rowCount()));
                }
            }
        });
    }

    private QueryStreamOptions normalize(QueryStreamOptions options) {
        if (options != null) {
            return options;
        }
        return new QueryStreamOptions("default-stream", properties.getFetchSize());
    }

    private void closeQuietly(ResultSet resultSet, PreparedStatement statement, Connection connection) {
        JdbcUtils.closeResultSet(resultSet);
        JdbcUtils.closeStatement(statement);
        DataSourceUtils.releaseConnection(connection, dataSource);
    }

    private static final class ResultSetIterator<T> implements java.util.Iterator<T> {
        private final ResultSet resultSet;
        private final RowMapper<T> rowMapper;
        private final Counter rowCounter;
        private boolean stateLoaded;
        private boolean hasNext;
        private T nextValue;
        private int rowNum;

        ResultSetIterator(ResultSet resultSet, RowMapper<T> rowMapper, Counter rowCounter) {
            this.resultSet = resultSet;
            this.rowMapper = rowMapper;
            this.rowCounter = rowCounter;
        }

        @Override
        public boolean hasNext() {
            loadState();
            return hasNext;
        }

        @Override
        public T next() {
            loadState();
            if (!hasNext) {
                throw new NoSuchElementException("No more rows available");
            }
            stateLoaded = false;
            return nextValue;
        }

        private void loadState() {
            if (stateLoaded) {
                return;
            }
            try {
                if (resultSet.next()) {
                    nextValue = rowMapper.mapRow(resultSet, rowNum++);
                    hasNext = true;
                    rowCounter.increment();
                } else {
                    hasNext = false;
                    nextValue = null;
                }
            } catch (SQLException ex) {
                throw new DataAccessResourceFailureException("Failed to stream result set", ex);
            }
            stateLoaded = true;
        }

        long rowCount() {
            return rowNum;
        }
    }
}
