package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.GlobalModelProfileEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserEnabledModelEntity;
import com.example.agentruntime.model.ModelAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserEnabledModelRepository extends JpaRepository<UserEnabledModelEntity, Long> {

    List<UserEnabledModelEntity> findByUserOrderByIsDefaultDescIdDesc(UserAccountEntity user);

    List<UserEnabledModelEntity> findByAccessStatusOrderByRequestedAtAsc(ModelAccessStatus accessStatus);

    Optional<UserEnabledModelEntity> findByUserAndModelProfile(UserAccountEntity user, GlobalModelProfileEntity modelProfile);

    Optional<UserEnabledModelEntity> findByUserAndIsDefaultTrue(UserAccountEntity user);
}
