package com.parallax.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository
        extends JpaRepository<ApplicationEntity, Long>, ApplicationRepositoryCustom {
}
