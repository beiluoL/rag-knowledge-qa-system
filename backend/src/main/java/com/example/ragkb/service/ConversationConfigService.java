package com.example.ragkb.service;

import com.example.ragkb.model.entity.ConversationConfig;
import com.example.ragkb.repository.ConversationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 对话配置服务：集中管理 AI 模式（offline/online）、AI 框架（spring-ai/langchain4j）
 * 以及 RAG 过程可视化开关。
 * <p>
 * - aiMode / aiFramework 复用 DynamicAiProvider / AiFrameworkRouter 的内存切换，并持久化到 DB；
 * - ragVisualizationEnabled 仅持久化，由前端 ChatView 读取；
 * - 应用启动时通过 loadFromDbOnStartup() 回填内存值，使重启保留上次切换。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationConfigService {

    private final DynamicAiProvider dynamicAiProvider;
    private final AiFrameworkRouter aiFrameworkRouter;
    private final ConversationConfigRepository configRepository;

    /**
     * 读取当前全量配置：内存中的模式/框架 + DB 持久化的可视化开关 + 当前 embedding 维度。
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aiMode", dynamicAiProvider.getMode());
        config.put("aiFramework", aiFrameworkRouter.getFramework());
        config.put("ragVisualizationEnabled", loadRagVisualizationEnabled());
        config.put("dimension", aiFrameworkRouter.getEmbeddingDimension());
        return config;
    }

    /**
     * 更新配置。参数均可为 null（只更新提供的字段）。
     */
    @Transactional
    public Map<String, Object> updateConfig(String aiMode,
                                             String aiFramework,
                                             Boolean ragVisualizationEnabled) {
        if (aiMode != null) {
            dynamicAiProvider.switchMode(aiMode);
        }
        if (aiFramework != null) {
            aiFrameworkRouter.switchFramework(aiFramework);
        }
        if (ragVisualizationEnabled != null) {
            ConversationConfig cfg = configRepository.findFirstByOrderByIdAsc()
                    .orElseGet(() -> {
                        ConversationConfig c = new ConversationConfig();
                        c.setId(1L);
                        return c;
                    });
            cfg.setAiMode(dynamicAiProvider.getMode());
            cfg.setAiFramework(aiFrameworkRouter.getFramework());
            cfg.setRagVisualizationEnabled(ragVisualizationEnabled);
            cfg.setUpdatedAt(LocalDateTime.now());
            configRepository.save(cfg);
            log.info("RAG 可视化开关已更新为: {}", ragVisualizationEnabled);
        }
        return getConfig();
    }

    private boolean loadRagVisualizationEnabled() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(ConversationConfig::getRagVisualizationEnabled)
                .orElse(true);
    }

    /**
     * 应用启动时从 DB 回填内存的模式 / 框架，使重启后保留上次的切换值。
     */
    public void loadFromDbOnStartup() {
        Optional<ConversationConfig> opt = configRepository.findFirstByOrderByIdAsc();
        if (opt.isEmpty()) {
            return;
        }
        ConversationConfig cfg = opt.get();
        if (cfg.getAiMode() != null) {
            dynamicAiProvider.switchMode(cfg.getAiMode());
        }
        if (cfg.getAiFramework() != null) {
            aiFrameworkRouter.switchFramework(cfg.getAiFramework());
        }
        log.info("已从数据库回填对话配置：aiMode={}, aiFramework={}, ragVisualizationEnabled={}",
                cfg.getAiMode(), cfg.getAiFramework(), cfg.getRagVisualizationEnabled());
    }
}
