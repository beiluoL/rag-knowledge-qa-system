package com.example.ragkb.repository;

import com.example.ragkb.model.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByParentId(Long parentId);

    List<KnowledgeBase> findByParentIdIsNull();

    List<KnowledgeBase> findByCategoryId(Long categoryId);

    List<KnowledgeBase> findByIsSystemTrue();

    List<KnowledgeBase> findByOwnerId(Long ownerId);

    Optional<KnowledgeBase> findByName(String name);

    List<KnowledgeBase> findAllByOrderBySortOrderAscCreatedAtDesc();
}
