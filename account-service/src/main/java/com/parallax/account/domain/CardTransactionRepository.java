package com.parallax.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardTransactionRepository extends JpaRepository<CardTransactionEntity, Long> {

    /** All transactions for an account, newest first (the detail shows the last 20). */
    List<CardTransactionEntity> findByAccountIdOrderByPostedAtDescIdDesc(Long accountId);
}
