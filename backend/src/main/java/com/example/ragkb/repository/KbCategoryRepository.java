package com.example.ragkb.repository;

import com.example.ragkb.model.entity.KbCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbCategoryRepository extends JpaRepository<KbCategory, Long> {
    List<KbCategory> findAllByOrderByNameAsc();
    Optional<KbCategory> findByName(String name);
}
