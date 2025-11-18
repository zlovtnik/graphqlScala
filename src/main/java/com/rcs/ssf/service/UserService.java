package com.rcs.ssf.service;

import com.rcs.ssf.dynamic.DynamicCrudColumnValue;
import com.rcs.ssf.dynamic.DynamicCrudFilter;
import com.rcs.ssf.dynamic.DynamicCrudGateway;
import com.rcs.ssf.dynamic.DynamicCrudOperation;
import com.rcs.ssf.dynamic.DynamicCrudRequest;
import com.rcs.ssf.dynamic.DynamicCrudResponse;
import com.rcs.ssf.dynamic.PlsqlInstrumentationSupport;
import com.rcs.ssf.entity.User;
import com.rcs.ssf.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);

    private final Optional<JdbcTemplate> jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DynamicCrudGateway dynamicCrudGateway;
    private final PlsqlInstrumentationSupport instrumentationSupport;
    private final UserRepository userRepository;

    private static final RowMapper<User> USER_ROW_MAPPER = new RowMapper<User>() {
        @Override
        public User mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            long id = rs.getLong("id");
            user.setId(rs.wasNull() ? null : id);
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            return user;
        }
    };

    public UserService(Optional<DataSource> dataSource,
            PasswordEncoder passwordEncoder,
            DynamicCrudGateway dynamicCrudGateway,
            @NonNull PlsqlInstrumentationSupport instrumentationSupport,
            UserRepository userRepository) {
        this.jdbcTemplate = dataSource.map(JdbcTemplate::new);
        this.passwordEncoder = passwordEncoder;
        this.dynamicCrudGateway = dynamicCrudGateway;
        this.instrumentationSupport = instrumentationSupport;
        this.userRepository = userRepository;
    }

    public Optional<User> findByUsername(String username) {
        if (hasJdbcSupport()) {
            return jdbcTemplate.get()
                    .execute((ConnectionCallback<Optional<User>>) (Connection con) -> instrumentationSupport
                            .withAction(con, "user_pkg", "get_user_by_username", () -> {
                                try (CallableStatement cs = con
                                        .prepareCall("{ ? = call user_pkg.get_user_by_username(?) }")) {
                                    cs.registerOutParameter(1, oracle.jdbc.OracleTypes.CURSOR);
                                    cs.setString(2, username);
                                    cs.execute();
                                    try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                                        User user = null;
                                        if (rs.next()) {
                                            user = USER_ROW_MAPPER.mapRow(rs, 1);
                                        }
                                        return Optional.ofNullable(user);
                                    }
                                }
                            }));
        }
        return blockOptional(userRepository.findByUsername(username));
    }

    public Optional<User> findByEmail(String email) {
        if (hasJdbcSupport()) {
            return jdbcTemplate.get()
                    .execute((ConnectionCallback<Optional<User>>) (Connection con) -> instrumentationSupport
                            .withAction(con, "user_pkg", "get_user_by_email", () -> {
                                try (CallableStatement cs = con
                                        .prepareCall("{ ? = call user_pkg.get_user_by_email(?) }")) {
                                    cs.registerOutParameter(1, oracle.jdbc.OracleTypes.CURSOR);
                                    cs.setString(2, email);
                                    cs.execute();
                                    try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                                        User user = null;
                                        if (rs.next()) {
                                            user = USER_ROW_MAPPER.mapRow(rs, 1);
                                        }
                                        return Optional.ofNullable(user);
                                    }
                                }
                            }));
        }
        return blockOptional(userRepository.findByEmail(email));
    }

    public User createUser(User user) {
        validateNewUser(user);
        ensureUsernameAvailable(user.getUsername(), null);
        ensureEmailAvailable(user.getEmail(), null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setId(null);

        List<DynamicCrudColumnValue> columns = List.of(
                new DynamicCrudColumnValue("username", user.getUsername()),
                new DynamicCrudColumnValue("password", user.getPassword()),
                new DynamicCrudColumnValue("email", user.getEmail()));

        DynamicCrudRequest request = new DynamicCrudRequest(
                "users",
                DynamicCrudOperation.CREATE,
                columns,
                null,
                null,
                null);

        if (hasJdbcSupport()) {
            DynamicCrudResponse response = dynamicCrudGateway.execute(request);
            log.debug("JDBC createUser response: affected={}, message={}, generatedId={}",
                    response.affectedRows(), response.message(), response.generatedId());
            response.optionalGeneratedId()
                    .map(this::parseId)
                    .ifPresentOrElse(
                            user::setId,
                            () -> log.warn("No generated ID returned from JDBC CREATE operation for user: {}",
                                    user.getUsername())
                    );
            if (user.getId() == null) {
                log.error("JDBC createUser failed to populate ID. Response: {}", response);
                throw new IllegalStateException("GENERATED_ID_NOT_FOUND");
            }
            return user;
        }

        // Use blocking repository save - R2dbcRepository returns Mono<User>, block it
        // Note: R2DBC won't populate the ID from a SEQUENCE-based primary key,
        // so we must fetch the user by username after insertion to populate the ID
        User savedUser = blockOptional(userRepository.save(user)).orElseThrow(() -> {
            log.error("Failed to persist new user: email={}, username={}. R2DBC save returned null.",
                    user.getEmail(), user.getUsername());
            return new IllegalStateException("USER_PERSIST_FAILED");
        });
        log.debug("R2DBC saved user: username={}, id={}", savedUser.getUsername(), savedUser.getId());
        
        // Fetch the persisted user by username to populate the generated ID
        // R2DBC doesn't return generated IDs for sequence-based PKs, so we refetch
        User persistedUser = findByUsername(user.getUsername())
                .orElseThrow(() -> {
                    log.error("Created user not found by username: {}. This should not happen.", user.getUsername());
                    return new IllegalStateException("USER_NOT_FOUND_AFTER_CREATE");
                });
        log.debug("R2DBC fetched persisted user: username={}, id={}", persistedUser.getUsername(),
                persistedUser.getId());
        
        if (persistedUser.getId() == null) {
            log.error("R2DBC createUser failed to populate ID. User: {}", persistedUser);
            throw new IllegalStateException("GENERATED_ID_NOT_FOUND");
        }
        
        return persistedUser;
    }

    public User updateUser(Long userId, Optional<String> newUsername, Optional<String> newEmail,
            Optional<String> newPassword) {
        User existing = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        newUsername.ifPresent(username -> {
            if (!username.equals(existing.getUsername())) {
                ensureUsernameAvailable(username, existing.getId());
                existing.setUsername(username);
            }
        });

        newEmail.ifPresent(email -> {
            if (!email.equals(existing.getEmail())) {
                validateEmailFormat(email);
                ensureEmailAvailable(email, existing.getId());
                existing.setEmail(email);
            }
        });

        List<DynamicCrudColumnValue> columns = new ArrayList<>(List.of(
                new DynamicCrudColumnValue("username", existing.getUsername()),
                new DynamicCrudColumnValue("email", existing.getEmail())));

        newPassword.ifPresent(password -> {
            validateRawPassword(password);
            String encodedPassword = passwordEncoder.encode(password);
            columns.add(new DynamicCrudColumnValue("password", encodedPassword));
        });

        List<DynamicCrudFilter> filters = List.of(new DynamicCrudFilter("id", "=", userId.toString()));

        DynamicCrudRequest request = new DynamicCrudRequest(
                "users",
                DynamicCrudOperation.UPDATE,
                columns,
                filters,
                null,
                null);

        if (hasJdbcSupport()) {
            dynamicCrudGateway.execute(request);
            return existing;
        }

        // Use blocking repository save - R2dbcRepository returns Mono<User>, block it
        User saved = blockOptional(userRepository.save(existing)).orElseThrow(() -> new RuntimeException(
                String.format("Failed to persist updated user: userId=%d, email=%s. Save returned null.",
                        userId, existing.getEmail())));
        return saved;
    }

    public boolean deleteUser(Long userId) {
        if (userId == null) {
            return false;
        }
        List<DynamicCrudFilter> filters = List.of(new DynamicCrudFilter("id", "=", userId.toString()));
        DynamicCrudRequest request = new DynamicCrudRequest(
                "users",
                DynamicCrudOperation.DELETE,
                null,
                filters,
                null,
                null);

        if (hasJdbcSupport()) {
            DynamicCrudResponse response = dynamicCrudGateway.execute(request);
            return response.affectedRows() > 0;
        }

        // Use blocking repository calls - R2dbcRepository returns Mono, block it
        // IMPORTANT: Block the deleteById() call to ensure deletion completes before
        // returning.
        // Without block(), the delete operation is not guaranteed to have completed
        // when the method returns,
        // creating a race condition where callers may check for deletion before it
        // actually finishes.
        if (blockOptional(userRepository.existsById(userId)).orElse(false)) {
            userRepository.deleteById(userId).block();
            return true;
        }
        return false;
    }

    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        if (hasJdbcSupport()) {
            return jdbcTemplate.get()
                    .execute((ConnectionCallback<Optional<User>>) (Connection con) -> instrumentationSupport
                            .withAction(con, "user_pkg", "get_user_by_id", () -> {
                                try (CallableStatement cs = con
                                        .prepareCall("{ ? = call user_pkg.get_user_by_id(?) }")) {
                                    cs.registerOutParameter(1, oracle.jdbc.OracleTypes.CURSOR);
                                    cs.setLong(2, id);
                                    cs.execute();
                                    try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                                        User user = null;
                                        if (rs.next()) {
                                            user = USER_ROW_MAPPER.mapRow(rs, 1);
                                        }
                                        return Optional.ofNullable(user);
                                    }
                                }
                            }));
        }
        return blockOptional(userRepository.findById(id));
    }

    private void validateNewUser(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new IllegalArgumentException("USERNAME_BLANK");
        }
        validateEmailFormat(user.getEmail());
        validateRawPassword(user.getPassword());
    }

    private void validateRawPassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("PASSWORD_BLANK");
        }
        if (looksEncoded(password)) {
            throw new IllegalArgumentException("PASSWORD_ENCODED");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("PASSWORD_TOO_SHORT");
        }
    }

    private void validateEmailFormat(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("EMAIL_BLANK");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("EMAIL_INVALID");
        }
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        // Fetch the user by username once to avoid TOCTOU (Time-of-Check-Time-of-Use)
        // races
        Optional<User> existingUser = findByUsername(username);

        if (existingUser.isPresent()) {
            User found = existingUser.get();
            // If a user with this username exists and is NOT the current user, it's in use
            if (!Objects.equals(found.getId(), currentUserId)) {
                throw new IllegalArgumentException("USERNAME_IN_USE");
            }
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        Optional<User> existingUser = findByEmail(email);
        if (existingUser.isPresent() && !Objects.equals(existingUser.get().getId(), currentUserId)) {
            throw new IllegalArgumentException("EMAIL_IN_USE");
        }
    }

    // NOTE: This heuristic only recognizes the bcrypt prefixes generated by
    // BCryptPasswordEncoder.
    // If the application switches to a different PasswordEncoder, revisit this
    // check.
    private boolean looksEncoded(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private boolean hasJdbcSupport() {
        return jdbcTemplate.isPresent();
    }

    /**
     * Block a Mono on the operation timeout, returning an Optional.
     * Centralizes the common pattern of mono.timeout(OPERATION_TIMEOUT).blockOptional()
     * used throughout this service for R2DBC operations.
     *
     * @param <T> the type of element emitted by the mono
     * @param mono the mono to block
     * @return Optional containing the result, or empty if mono completes empty
     */
    private <T> Optional<T> blockOptional(Mono<T> mono) {
        return mono.timeout(OPERATION_TIMEOUT).blockOptional();
    }

    private Long parseId(String rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Unable to parse generated id: " + rawId, ex);
        }
    }
}
