package com.parallax.application.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerGrantsIT extends AbstractLedgerIT {

    @Test
    void appRoleCannotUpdateOrDeleteLedgerRows() {
        appendInTx(decisionEntry(insertApplication("APP-G"), "APP-G"));

        assertThatThrownBy(() -> jdbc.update("UPDATE decision_ledger SET outcome = 'APPROVED'"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("42501"));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM decision_ledger"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("42501"));
    }

    private static String sqlState(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
            if (cause instanceof UncategorizedSQLException un && un.getSQLException() != null) {
                return un.getSQLException().getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
