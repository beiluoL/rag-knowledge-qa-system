package com.example.ragkb.service;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.repository.KbCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 知识库分类管理（动态分类，可由用户新增） */
@Service
@RequiredArgsConstructor
public class KbCategoryService {

    private final KbCategoryRepository kbCategoryRepository;

    public List<KbCategory> listAll() {
        return kbCategoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public KbCategory create(String name, String description) {
        if (name == null || name.isBlank()) throw new BusinessException("分类名称不能为空");
        if (kbCategoryRepository.findByName(name).isPresent()) {
            throw new BusinessException("分类已存在");
        }
        return kbCategoryRepository.save(KbCategory.builder()
                .name(name.trim()).description(description).build());
    }

    @Transactional
    public void delete(Long id) {
        kbCategoryRepository.deleteById(id);
    }
}
