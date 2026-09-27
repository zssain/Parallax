package com.parallax.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementRepository extends JpaRepository<StatementEntity, Long> {

    long countByAccountId(Long accountId);

    /** All statements for an account, newest first. */
    List<StatementEntity> findByAccountIdOrderByPeriodEndDescIdDesc(Long accountId);

    /** The most recent (previous) statement, if any. */
    Optional<StatementEntity> findFirstByAccountIdOrderByPeriodEndDescIdDesc(Long accountId);
}
