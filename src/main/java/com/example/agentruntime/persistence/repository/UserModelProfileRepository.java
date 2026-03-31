package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserModelProfileRepository extends JpaRepository<UserModelProfileEntity, Long> {

    List<UserModelProfileEntity> findByUserOrderByIsDefaultDescIdDesc(UserAccountEntity user);

    Optional<UserModelProfileEntity> findByUserAndId(UserAccountEntity user, Long id);

    Optional<UserModelProfileEntity> findByUserAndProfileName(UserAccountEntity user, String profileName);

    Optional<UserModelProfileEntity> findByUserAndIsDefaultTrue(UserAccountEntity user);
}
