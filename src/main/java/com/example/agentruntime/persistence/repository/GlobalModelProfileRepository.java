package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.GlobalModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GlobalModelProfileRepository extends JpaRepository<GlobalModelProfileEntity, Long> {

    List<GlobalModelProfileEntity> findByEnabledTrueOrderByIdDesc();

    List<GlobalModelProfileEntity> findAllByOrderByIdDesc();

    Optional<GlobalModelProfileEntity> findByProfileName(String profileName);
}
