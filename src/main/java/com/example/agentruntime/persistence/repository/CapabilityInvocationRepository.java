package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.CapabilityInvocationEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CapabilityInvocationRepository extends JpaRepository<CapabilityInvocationEntity, Long> {

    List<CapabilityInvocationEntity> findByUserOrderByIdDesc(UserAccountEntity user);

    List<CapabilityInvocationEntity> findByUserAndConversationIdOrderByIdDesc(UserAccountEntity user, String conversationId);
}
