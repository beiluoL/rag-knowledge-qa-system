package com.example.ragkb.repository;

import com.example.ragkb.model.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Document> findByStatus(String status);

    @Query("SELECT COUNT(d) FROM Document d")
    long countAll();

    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM Document d")
    long sumChunkCount();

    // 搜索：按标题或标签关键词
    Page<Document> findByTitleContainingIgnoreCaseOrTagsContainingIgnoreCase(
            String titleKeyword, String tagsKeyword, Pageable pageable);

    /** 按状态筛选文档（分页） */
    Page<Document> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
