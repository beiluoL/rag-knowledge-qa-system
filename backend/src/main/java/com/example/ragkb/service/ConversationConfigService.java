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
 * RAG 过程可视化开关、真 SSE 流式开关，以及混合检索开关与 RRF 融合常数。
 * <p>
 * - aiMode / aiFramework 复用 DynamicAiProvider / AiFrameworkRouter 的内存切换，并持久化到 DB；
 * - ragVisualizationEnabled / trueSseStreamingEnabled / hybridEnabled / rrfK 仅持久化，
 *   其中 trueSseStreamingEnabled 由 ChatController 每次请求实时读取，hybridEnabled / rrfK 由 RAGService 实时读取；
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
     * 读取当前全量配置：内存中的模式/框架 + DB 持久化的可视化开关 + 真 SSE 流式开关
     * + 混合检索开关 + RRF 常数 + 当前 embedding 维度。
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aiMode", dynamicAiProvider.getMode());
        config.put("aiFramework", aiFrameworkRouter.getFramework());
        config.put("ragVisualizationEnabled", loadRagVisualizationEnabled());
        config.put("trueSseStreamingEnabled", loadTrueSseStreamingEnabled());
        config.put("hybridEnabled", loadHybridEnabled());
        config.put("rrfK", loadRrfK());
        config.put("dimension", aiFrameworkRouter.getEmbeddingDimension());
        return config;
    }

    /**
     * 更新配置。参数均可为 null（只更新提供的字段）。
     */
    @Transactional
    public Map<String, Object> updateConfig(String aiMode,
                                             String aiFramework,
                                             Boolean ragVisualizationEnabled,
                                             Boolean trueSseStreamingEnabled,
                                             Boolean hybridEnabled,
                                             Integer rrfK) {
        if (aiMode != null) {
            dynamicAiProvider.switchMode(aiMode);
        }
        if (aiFramework != null) {
            aiFrameworkRouter.switchFramework(aiFramework);
        }
        if (ragVisualizationEnabled != null || trueSseStreamingEnabled != null
                || hybridEnabled != null || rrfK != null) {
            ConversationConfig cfg = configRepository.findFirstByOrderByIdAsc()
                    .orElseGet(() -> {
                        ConversationConfig c = new ConversationConfig();
                        c.setId(1L);
                        return c;
                    });
            cfg.setAiMode(dynamicAiProvider.getMode());
            cfg.setAiFramework(aiFrameworkRouter.getFramework());
            if (ragVisualizationEnabled != null) {
                cfg.setRagVisualizationEnabled(ragVisualizationEnabled);
            }
            if (trueSseStreamingEnabled != null) {
                cfg.setTrueSseStreamingEnabled(trueSseStreamingEnabled);
            }
            if (hybridEnabled != null) {
                cfg.setHybridEnabled(hybridEnabled);
            }
            if (rrfK != null) {
                cfg.setRrfK(rrfK);
            }
            cfg.setUpdatedAt(LocalDateTime.now());
            configRepository.save(cfg);
            log.info("对话配置已更新: ragVisualizationEnabled={}, trueSseStreamingEnabled={}, hybridEnabled={}, rrfK={}",
                    cfg.getRagVisualizationEnabled(), cfg.getTrueSseStreamingEnabled(),
                    cfg.getHybridEnabled(), cfg.getRrfK());
        }
        return getConfig();
    }

    /**
     * 实时读取「真 SSE 流式」开关（供 ChatController 在每次请求时决定生成策略）。
     * 取不到配置时默认 true（真流式）。
     */
    public boolean isTrueSseStreamingEnabled() {
        return loadTrueSseStreamingEnabled();
    }

    /**
     * 实时读取「混合检索」开关（供 RAGService 在每次检索时决定走纯向量还是混合）。
     * 取不到配置时默认 true（开启混合检索）。
     */
    public boolean isHybridEnabled() {
        return loadHybridEnabled();
    }

    /**
     * 实时读取 RRF 融合常数 k（供 RAGService 融合时计算排名权重）。
     * 取不到配置时默认 60。
     */
    public int getRrfK() {
        return loadRrfK();
    }

    private boolean loadRagVisualizationEnabled() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(ConversationConfig::getRagVisualizationEnabled)
                .orElse(true);
    }

    private boolean loadTrueSseStreamingEnabled() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(ConversationConfig::getTrueSseStreamingEnabled)
                .orElse(true);
    }

    private boolean loadHybridEnabled() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(ConversationConfig::getHybridEnabled)
                .orElse(true);
    }

    private int loadRrfK() {
        return configRepository.findFirstByOrderByIdAsc()
                .map(ConversationConfig::getRrfK)
                .orElse(60);
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
        log.info("已从数据库回填对话配置：aiMode={}, aiFramework={}, ragVisualizationEnabled={}, trueSseStreamingEnabled={}, hybridEnabled={}, rrfK={}",
                cfg.getAiMode(), cfg.getAiFramework(), cfg.getRagVisualizationEnabled(),
                cfg.getTrueSseStreamingEnabled(), cfg.getHybridEnabled(), cfg.getRrfK());
    }
}
