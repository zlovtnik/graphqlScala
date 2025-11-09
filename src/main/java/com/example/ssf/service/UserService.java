package com.example.ssf.service;

import com.example.ssf.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private static final RowMapper<User> USER_ROW_MAPPER = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(UUID.fromString(rs.getString("id")));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            return user;
        }
    };

    public UserService(DataSource dataSource, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.get_user_by_username(?) }")) {
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
        });
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.get_user_by_email(?) }")) {
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
        });
    }

    @Transactional
    public User createUser(User user) {
        validateNewUser(user);
        ensureUsernameAvailable(user.getUsername(), null);
        ensureEmailAvailable(user.getEmail(), null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.create_user(?, ?, ?, ?) }")) {
                cs.setString(1, user.getUsername());
                cs.setString(2, user.getPassword()); // Already hashed
                cs.setString(3, user.getEmail());
                cs.registerOutParameter(4, java.sql.Types.VARCHAR);
                cs.execute();
                String userId = cs.getString(4);
                user.setId(UUID.fromString(userId));
                return user;
            }
        });
    }

    @Transactional
    public User updateUser(UUID userId, String newUsername, String newEmail, Optional<String> newPassword) {
        User existing = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        if (StringUtils.hasText(newUsername) && !newUsername.equals(existing.getUsername())) {
            ensureUsernameAvailable(newUsername, existing.getId());
            existing.setUsername(newUsername);
        }

        if (StringUtils.hasText(newEmail) && !newEmail.equals(existing.getEmail())) {
            validateEmailFormat(newEmail);
            ensureEmailAvailable(newEmail, existing.getId());
            existing.setEmail(newEmail);
        }

        newPassword.ifPresent(password -> {
            validateRawPassword(password);
            existing.setPassword(passwordEncoder.encode(password));
        });

        // Call update procedure
        jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.update_user(?, ?, ?, ?) }")) {
                cs.setString(1, userId.toString());
                cs.setString(2, existing.getUsername());
                cs.setString(3, existing.getEmail());
                cs.setString(4, existing.getPassword());
                cs.execute();
                return null;
            }
        });

        return existing;
    }

    @Transactional
    public boolean deleteUser(UUID userId) {
        // Call delete procedure
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.delete_user(?) }")) {
                cs.registerOutParameter(1, java.sql.Types.INTEGER);
                cs.setString(2, userId.toString());
                cs.execute();
                int deleted = cs.getInt(1);
                return deleted > 0;
            }
        });
    }

    public Optional<User> findById(UUID id) {
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.get_user_by_id(?) }")) {
                cs.registerOutParameter(1, oracle.jdbc.OracleTypes.CURSOR);
                cs.setString(2, id.toString());
                cs.execute();
                try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                    User user = null;
                    if (rs.next()) {
                        user = USER_ROW_MAPPER.mapRow(rs, 1);
                    }
                    return Optional.ofNullable(user);
                }
            }
        });
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

    private void ensureUsernameAvailable(String username, UUID currentUserId) {
        boolean exists = jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.username_exists(?) }")) {
                cs.registerOutParameter(1, java.sql.Types.BOOLEAN);
                cs.setString(2, username);
                cs.execute();
                boolean result = cs.getBoolean(1);
                return result;
            }
        });
        if (exists) {
            // Check if it's the current user
            if (currentUserId != null) {
                Optional<User> existingUser = findByUsername(username);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(currentUserId)) {
                    throw new IllegalArgumentException("USERNAME_IN_USE");
                }
            } else {
                throw new IllegalArgumentException("USERNAME_IN_USE");
            }
        }
    }

    private void ensureEmailAvailable(String email, UUID currentUserId) {
        boolean exists = jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ ? = call user_pkg.email_exists(?) }")) {
                cs.registerOutParameter(1, java.sql.Types.BOOLEAN);
                cs.setString(2, email);
                cs.execute();
                boolean result = cs.getBoolean(1);
                return result;
            }
        });
        if (exists) {
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
    }

    // NOTE: This heuristic only recognizes the bcrypt prefixes generated by BCryptPasswordEncoder.
    // If the application switches to a different PasswordEncoder, revisit this check.
    private boolean looksEncoded(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
