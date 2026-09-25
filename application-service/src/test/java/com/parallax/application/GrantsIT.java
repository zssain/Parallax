package com.parallax.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantsIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc; // uses the runtime datasource (parallax_app)

    @Test
    void appRoleCannotCreateTables() {
        // parallax_app has only USAGE on schema public, never CREATE.
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE x(i int)"))
                .isInstanceOf(DataAccessException.class);
    }
}
