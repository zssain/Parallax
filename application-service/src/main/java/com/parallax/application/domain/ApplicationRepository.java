package com.parallax.application.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationRepository
        extends JpaRepository<ApplicationEntity, Long>, ApplicationRepositoryCustom {

    Optional<ApplicationEntity> findByPublicId(String publicId);
}
