package com.example.ragkb.controller;

import com.example.ragkb.model.entity.KbCategory;
import com.example.ragkb.service.KbCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 知识库分类接口（动态分类） */
@RestController
@RequestMapping("/api/kb-categories")
@RequiredArgsConstructor
public class KbCategoryController {

    private final KbCategoryService kbCategoryService;

    @GetMapping
    public ResponseEntity<List<KbCategory>> list() {
        return ResponseEntity.ok(kbCategoryService.listAll());
    }

    @PostMapping
    public ResponseEntity<KbCategory> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(kbCategoryService.create(body.get("name"), body.get("description")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        kbCategoryService.delete(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }
}
