package com.example.ragkb.repository;

import com.example.ragkb.model.entity.ConversationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationConfigRepository extends JpaRepository<ConversationConfig, Long> {

    Optional<ConversationConfig> findFirstByOrderByIdAsc();
}
