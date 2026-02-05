package com.alibaba.cloud.ai.copilot.store;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 偏好去重器
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferenceDeduplicator {

    private final AppProperties appProperties;

    /**
     * 判断两个偏好是否相似
     *
     * @return 相似度分数（0.0-1.0）
     */
    public double calculateSimilarity(PreferenceInfo p1, PreferenceInfo p2) {
        // 1. 类别必须相同
        if (p1.getCategory() == null || p2.getCategory() == null ||
                !p1.getCategory().equals(p2.getCategory())) {
            return 0.0;
        }

        // 2. 值相似度（支持同义词和模糊匹配）
        double valueSimilarity = calculateValueSimilarity(p1.getValue(), p2.getValue());

        // 3. 上下文相似度（可选）
        double contextSimilarity = calculateContextSimilarity(p1.getContext(), p2.getContext());

        // 加权平均
        return valueSimilarity * 0.8 + contextSimilarity * 0.2;
    }

    /**
     * 值相似度计算
     * - 完全匹配：1.0
     * - 包含关系：0.7（如 "Java" 和 "Java编程"）
     * - 同义词：0.6（需要同义词词典）
     * - 语义相似：0.5（使用向量相似度，可选）
     */
    private double calculateValueSimilarity(String v1, String v2) {
        if (v1 == null || v2 == null) {
            return 0.0;
        }

        if (v1.equalsIgnoreCase(v2)) {
            return 1.0;
        }

        // 包含关系
        String v1Lower = v1.toLowerCase();
        String v2Lower = v2.toLowerCase();
        if (v1Lower.contains(v2Lower) || v2Lower.contains(v1Lower)) {
            return 0.7;
        }

        // 同义词匹配（需要维护同义词词典）
        if (appProperties.getMemory().getDeduplication().isEnableSynonymMatching()) {
            if (isSynonym(v1Lower, v2Lower)) {
                return 0.6;
            }
        }

        // 可以使用向量相似度（如果集成了向量数据库）
        if (appProperties.getMemory().getDeduplication().isEnableSemanticSimilarity()) {
            // return vectorSimilarity(v1, v2);
        }

        return 0.0;
    }

    /**
     * 上下文相似度计算
     */
    private double calculateContextSimilarity(String c1, String c2) {
        if (c1 == null || c2 == null || c1.isEmpty() || c2.isEmpty()) {
            return 0.5; // 如果上下文为空，给中等相似度
        }

        // 简单的字符串包含判断
        String c1Lower = c1.toLowerCase();
        String c2Lower = c2.toLowerCase();
        if (c1Lower.contains(c2Lower) || c2Lower.contains(c1Lower)) {
            return 0.8;
        }

        // 可以使用更复杂的相似度算法（如编辑距离）
        return 0.0;
    }

    /**
     * 同义词判断（简化实现，实际应该使用同义词词典）
     */
    private boolean isSynonym(String v1, String v2) {
        // TODO: 实现同义词词典
        // 这里可以维护一个同义词映射表
        return false;
    }

    /**
     * 去重处理逻辑
     */
    public PreferenceInfo deduplicate(PreferenceInfo newPref, List<PreferenceInfo> existing) {
        double threshold = appProperties.getMemory().getDeduplication().getSimilarityThreshold();

        for (PreferenceInfo existingPref : existing) {
            double similarity = calculateSimilarity(newPref, existingPref);

            if (similarity > threshold) {
                // 高度相似，合并：更新置信度和使用次数
                log.debug("发现相似偏好，合并: {} vs {}, 相似度: {}", newPref.getValue(), existingPref.getValue(), similarity);
                return mergePreferences(existingPref, newPref);
            } else if (similarity > 0.5) {
                // 中等相似，提升置信度
                existingPref.setConfidence(
                        Math.max(existingPref.getConfidence() != null ? existingPref.getConfidence() : 0.0,
                                newPref.getConfidence() != null ? newPref.getConfidence() : 0.0)
                );
                existingPref.setUsageCount(
                        (existingPref.getUsageCount() != null ? existingPref.getUsageCount() : 0) + 1
                );
                log.debug("发现中等相似偏好，提升置信度: {} vs {}, 相似度: {}", newPref.getValue(), existingPref.getValue(), similarity);
                return existingPref; // 返回已存在的，不添加新的
            }
        }

        // 没有相似项，返回新偏好
        return newPref;
    }

    /**
     * 合并两个相似偏好
     */
    private PreferenceInfo mergePreferences(PreferenceInfo existing, PreferenceInfo newPref) {
        // 保留置信度更高的
        double existingConf = existing.getConfidence() != null ? existing.getConfidence() : 0.0;
        double newConf = newPref.getConfidence() != null ? newPref.getConfidence() : 0.0;

        if (newConf > existingConf) {
            existing.setConfidence(newConf);
            if (newPref.getContext() != null && !newPref.getContext().isEmpty()) {
                existing.setContext(newPref.getContext()); // 更新上下文
            }
        }

        // 增加使用次数
        existing.setUsageCount((existing.getUsageCount() != null ? existing.getUsageCount() : 0) + 1);

        // 更新学习时间
        existing.setLearnedAt(LocalDateTime.now());

        return existing;
    }
}
