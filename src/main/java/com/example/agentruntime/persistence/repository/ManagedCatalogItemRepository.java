package com.example.agentruntime.persistence.repository;

import com.example.agentruntime.persistence.entity.ManagedCatalogItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagedCatalogItemRepository extends JpaRepository<ManagedCatalogItemEntity, Long> {

    Optional<ManagedCatalogItemEntity> findByItemIdAndItemType(String itemId, String itemType);

    List<ManagedCatalogItemEntity> findByItemTypeAndStatus(String itemType, String status);
}
