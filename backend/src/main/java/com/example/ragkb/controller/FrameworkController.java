package com.example.ragkb.controller;

import com.example.ragkb.service.AiFrameworkRouter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-framework")
public class FrameworkController {

    private final AiFrameworkRouter frameworkRouter;

    public FrameworkController(AiFrameworkRouter frameworkRouter) {
        this.frameworkRouter = frameworkRouter;
    }

    /**
     * 获取当前 AI 框架（及当前模式、向量维度）
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getFramework() {
        return ResponseEntity.ok(Map.of(
                "framework", frameworkRouter.getFramework(),
                "mode", frameworkRouter.getMode(),
                "dimension", String.valueOf(frameworkRouter.getEmbeddingDimension())
        ));
    }

    /**
     * 切换 AI 框架（spring-ai / langchain4j）
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, String>> switchFramework(@RequestBody Map<String, String> body) {
        String target = body.getOrDefault("framework", "spring-ai");
        String fw = frameworkRouter.switchFramework(target);
        String label = "langchain4j".equals(fw) ? "LangChain4j" : "Spring AI";
        return ResponseEntity.ok(Map.of(
                "framework", fw,
                "mode", frameworkRouter.getMode(),
                "message", "已切换为 " + label + " 框架"
        ));
    }
}
