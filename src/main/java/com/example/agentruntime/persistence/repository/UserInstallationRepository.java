package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserInstallationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInstallationRepository extends JpaRepository<UserInstallationEntity, Long> {

    List<UserInstallationEntity> findByUser(UserAccountEntity user);

    List<UserInstallationEntity> findByUserAndItemTypeAndStatus(UserAccountEntity user, String itemType, String status);

    List<UserInstallationEntity> findByUserAndItemType(UserAccountEntity user, String itemType);

    Optional<UserInstallationEntity> findByUserAndItemIdAndItemType(UserAccountEntity user, String itemId, String itemType);

    void deleteByUserAndItemIdAndItemType(UserAccountEntity user, String itemId, String itemType);
}
