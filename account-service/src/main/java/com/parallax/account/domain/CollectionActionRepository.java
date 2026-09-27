package com.parallax.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectionActionRepository extends JpaRepository<CollectionActionEntity, Long> {

    /** The most recent collections touch on an account (its lastContactAt). */
    Optional<CollectionActionEntity> findFirstByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);
}
