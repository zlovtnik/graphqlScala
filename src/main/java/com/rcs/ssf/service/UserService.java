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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);


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
        return userRepository.findByUsername(username).blockOptional();
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
        return userRepository.findByEmail(email).blockOptional();
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
                response.optionalGeneratedId()
                    .map(this::parseId)
                    .ifPresent(user::setId);
            return user;
        }

        // Use blocking repository save - R2dbcRepository returns Mono<User>, block it
        User saved = userRepository.save(user).block();
        if (saved == null) {
            throw new RuntimeException(
                String.format("Failed to persist new user: email=%s, username=%s. Save returned null.",
                    user.getEmail(), user.getUsername()));
        }
        return saved;
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
        User saved = userRepository.save(existing).block();
        if (saved == null) {
            throw new RuntimeException(
                String.format("Failed to persist updated user: userId=%d, email=%s. Save returned null.",
                    userId, existing.getEmail()));
        }
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
        // IMPORTANT: Block the deleteById() call to ensure deletion completes before returning.
        // Without block(), the delete operation is not guaranteed to have completed when the method returns,
        // creating a race condition where callers may check for deletion before it actually finishes.
        if (userRepository.existsById(userId).block()) {
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
        return userRepository.findById(id).blockOptional();
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
        // Fetch the user by username once to avoid TOCTOU (Time-of-Check-Time-of-Use) races
        Optional<User> existingUser = findByUsername(username);
        
        if (existingUser.isPresent()) {
            User found = existingUser.get();
            // If a user with this username exists and is NOT the current user, it's in use
            if (currentUserId == null || !found.getId().equals(currentUserId)) {
                throw new IllegalArgumentException("USERNAME_IN_USE");
            }
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        if (hasJdbcSupport()) {
            Boolean exists = jdbcTemplate.get()
                    .execute((ConnectionCallback<Boolean>) (Connection con) -> instrumentationSupport.withAction(con,
                            "user_pkg", "email_exists", () -> {
                                try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.email_exists(?) }")) {
                                    cs.registerOutParameter(1, java.sql.Types.BOOLEAN);
                                    cs.setString(2, email);
                                    cs.execute();
                                    boolean result = cs.getBoolean(1);
                                    if (cs.wasNull())
                                        result = false;
                                    return result;
                                }
                            }));
            if (Boolean.TRUE.equals(exists)) {
                // Check if it's the current user
                if (currentUserId != null) {
                    Optional<User> existingUser = findByEmail(email);
                    if (existingUser.isPresent() && !existingUser.get().getId().equals(currentUserId)) {
                        throw new IllegalArgumentException("EMAIL_IN_USE");
                    }
                } else {
                    throw new IllegalArgumentException("EMAIL_IN_USE");
                }
            }
            return;
        }

        Optional<User> existingUser = findByEmail(email);
        if (existingUser.isPresent() && (currentUserId == null || !existingUser.get().getId().equals(currentUserId))) {
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
