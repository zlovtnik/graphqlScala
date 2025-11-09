package com.example.ssf.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    public AuditService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void logLoginAttempt(String username, boolean success, String ipAddress, String userAgent, String failureReason) {
        jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall("{ call user_pkg.log_login_attempt(?, ?, ?, ?, ?) }");
            cs.setString(1, username);
            cs.setInt(2, success ? 1 : 0);
            cs.setString(3, ipAddress);
            cs.setString(4, userAgent);
            cs.setString(5, failureReason);
            cs.execute();
            cs.close();
            return null;
        });
    }

    public void logSessionStart(String userId, String token, String ipAddress, String userAgent) {
        String tokenHash = hashToken(token);
        jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall("{ call user_pkg.log_session_start(?, ?, ?, ?) }");
            cs.setString(1, userId);
            cs.setString(2, tokenHash);
            cs.setString(3, ipAddress);
            cs.setString(4, userAgent);
            cs.execute();
            cs.close();
            return null;
        });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}