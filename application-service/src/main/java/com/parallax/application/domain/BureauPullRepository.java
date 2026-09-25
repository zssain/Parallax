package com.parallax.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface BureauPullRepository extends JpaRepository<BureauPullEntity, String> {

    /** The most recent pull for this ssn_token + pull type within the reuse window (SPEC §3 step 4). */
    Optional<BureauPullEntity> findFirstBySsnTokenAndPullTypeAndPulledAtGreaterThanEqualOrderByPulledAtDesc(
            String ssnToken, String pullType, Instant cutoff);
}
