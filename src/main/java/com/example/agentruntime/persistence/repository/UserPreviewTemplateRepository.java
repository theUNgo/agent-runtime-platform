package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserPreviewTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户自定义试跑模板仓储。
 */
public interface UserPreviewTemplateRepository extends JpaRepository<UserPreviewTemplateEntity, Long> {

    List<UserPreviewTemplateEntity> findByUserOrderByUpdatedAtDesc(UserAccountEntity user);

    Optional<UserPreviewTemplateEntity> findByUserAndTemplateKey(UserAccountEntity user, String templateKey);
}
