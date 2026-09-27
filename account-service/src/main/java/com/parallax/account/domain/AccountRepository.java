package com.parallax.account.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByPublicId(String publicId);

    Optional<AccountEntity> findByApplicationId(String applicationId);

    Page<AccountEntity> findByStatus(AccountStatus status, Pageable pageable);

    /** Every past-due account (days_past_due > 0), worst first — the collections work queue. */
    List<AccountEntity> findByDaysPastDueGreaterThanOrderByDaysPastDueDesc(int threshold);
}
