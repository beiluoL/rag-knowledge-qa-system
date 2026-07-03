package com.example.ragkb.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好的文本切分工具
 * 滑动窗口策略：chunk 之间有 overlap 保证上下文连贯
 */
@Component
@Slf4j
public class TextSplitter {

    /**
     * 将文本按指定大小切分为多个 chunk
     *
     * @param text     原始文本
     * @param chunkSize   每个 chunk 的最大字符数
     * @param overlap     相邻 chunk 之间的重叠字符数
     * @return 切分后的文本块列表
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        // 清理文本：合并多余空白
        text = text.replaceAll("\\s+", " ").trim();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // 先按段落分割
        String[] paragraphs = text.split("(?<=[。！？\\n])");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 如果当前块加上新段落超出限制，保存当前块
            if (currentChunk.length() + paragraph.length() > chunkSize
                    && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());

                // 保留 overlap 部分的内容
                String remaining = currentChunk.toString();
                if (remaining.length() > overlap) {
                    currentChunk = new StringBuilder(
                            remaining.substring(remaining.length() - overlap));
                } else {
                    currentChunk = new StringBuilder(remaining);
                }
            }

            currentChunk.append(paragraph);
        }

        // 保存最后一个 chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 估算中文 token 数量（粗略：1 个中文字 ≈ 1.5 token）
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
    }
}
