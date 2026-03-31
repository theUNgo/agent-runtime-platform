package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.AgentConversationEntity;
import com.example.agentruntime.persistence.entity.AgentMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessageEntity, Long> {

    List<AgentMessageEntity> findByConversationOrderByIdAsc(AgentConversationEntity conversation);
}
