package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.AgentConversationEntity;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {

    Optional<AgentConversationEntity> findByConversationId(String conversationId);

    List<AgentConversationEntity> findByUserOrderByIdDesc(UserAccountEntity user);
}
