package com.parallax.application.jobs;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs a job body while holding a Postgres session advisory lock on a single connection (SPEC §6), so
 * only one instance runs it at a time. Returns an empty list if the lock cannot be acquired.
 */
final class JobAdvisoryLock {

    private JobAdvisoryLock() {
    }

    static List<String> runGuarded(JdbcTemplate jdbcTemplate, long lockKey, Supplier<List<String>> body) {
        ConnectionCallback<List<String>> callback = (Connection connection) -> {
            if (!tryLock(connection, lockKey)) {
                return List.of();
            }
            try {
                return body.get();
            } finally {
                unlock(connection, lockKey);
            }
        };
        return jdbcTemplate.execute(callback);
    }

    private static boolean tryLock(Connection connection, long lockKey) throws java.sql.SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, lockKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection, long lockKey) throws java.sql.SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, lockKey);
            ps.execute();
        }
    }
}
