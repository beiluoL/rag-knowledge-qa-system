package com.example.ragkb.controller;

import com.example.ragkb.exception.BusinessException;
import com.example.ragkb.model.entity.UserMemory;
import com.example.ragkb.repository.UserMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final UserMemoryRepository memoryRepository;

    public MemoryController(UserMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /** 查看我自己的记忆（按重要性、时间倒序） */
    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return ResponseEntity.ok(memoryRepository.findByUserIdOrderByImportanceDescCreatedAtDesc(userId));
    }

    /** 编辑某条记忆（内容 / 重要性） */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body,
                                    Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        UserMemory m = memoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("记忆不存在"));
        if (!m.getUserId().equals(userId)) throw new BusinessException("无权操作该记忆");
        if (body.containsKey("content")) m.setContent(((String) body.get("content")).trim());
        if (body.containsKey("importance")) {
            int imp = ((Number) body.get("importance")).intValue();
            m.setImportance(Math.max(1, Math.min(5, imp)));
        }
        memoryRepository.save(m);
        return ResponseEntity.ok(Map.of("message", "更新成功"));
    }

    /** 删除某条记忆 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        memoryRepository.deleteByUserIdAndId(userId, id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }
}
