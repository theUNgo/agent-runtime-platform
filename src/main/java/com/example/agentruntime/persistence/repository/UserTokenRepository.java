package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.UserTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserTokenEntity, Long> {

    Optional<UserTokenEntity> findByToken(String token);

    void deleteByToken(String token);

    void deleteByExpiresAtBefore(Instant instant);
}
