package com.geosaa.modules.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器。
 *
 * <p>采用固定大小分块 + 重叠策略，保证上下文连续性。
 * 默认 chunkSize=500 字符，overlap=50 字符。
 * 优先按段落/句号分割，不足时按字符硬切。
 */
@Component
public class TextChunker {

    @Value("${rag.chunk.size:500}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:50}")
    private int overlap;

    /**
     * 将文本分块。
     *
     * @param text 原始文本
     * @return 分块后的文本列表
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String normalized = text.trim();
        int length = normalized.length();

        if (length <= chunkSize) {
            chunks.add(normalized);
            return chunks;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            // 尝试在句号/段落边界处截断，避免切断句子
            if (end < length) {
                int boundary = findSentenceBoundary(normalized, start, end);
                if (boundary > start + chunkSize / 2) {
                    end = boundary;
                }
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }
            // 下一块从重叠位置开始
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    /**
     * 在 [start, end] 范围内寻找最近的句子边界（句号、问号、感叹号、换行）。
     * 从 end 向前找，返回边界位置（包含标点符号）。
     */
    private int findSentenceBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '!' ||
                c == '？' || c == '?' || c == '\n' || c == '；' || c == ';') {
                return i + 1;
            }
        }
        return end;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }
}
