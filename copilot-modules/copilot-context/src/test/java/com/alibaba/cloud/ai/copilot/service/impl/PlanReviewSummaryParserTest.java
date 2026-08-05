package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanReviewSummaryParserTest {

    private final PlanReviewSummaryParser parser = new PlanReviewSummaryParser();

    @Test
    void extractsApprovalFriendlySectionsFromStandardPlan() {
        PlanWorkspaceDTO.PlanReview review = parser.parse("""
                ## 任务理解
                - 要解决的问题是：统一登录失败提示
                - 涉及文件：`ui-react/src/LoginForm.tsx`、`AuthService.java`
                - 不碰的范围：数据库结构、公开 API

                ## 方案设计
                - 选择方案及原因：统一由前端映射错误码。

                ## 变更清单
                | 文件 | 操作 | 影响范围 |
                |------|------|----------|
                | `ui-react/src/LoginForm.tsx` | 修改提示 | 登录表单 |
                | `AuthService.java` | 补充错误码 | 鉴权服务 |

                ## 测试策略
                - 新增或更新的测试：执行 `pnpm test` 和 `mvn test -pl copilot-context`
                - 手动验证：输入错误密码时显示明确提示

                ## 风险点
                - [ ] 数据库 migration 或不可逆操作：无
                - [x] 外部 API 调用：需要保持既有错误码兼容

                ## 待确认（没有则写“无”）
                - 是否保留服务端原始错误信息？建议不保留。
                """);

        assertEquals("统一登录失败提示", review.getSummary());
        assertEquals(2, review.getChanges().size());
        assertEquals("修改提示 · ui-react/src/LoginForm.tsx", review.getChanges().getFirst().getTitle());
        assertEquals(2, review.getVerifications().size());
        assertEquals(List.of("数据库结构", "公开 API"), review.getScopeOut());
        assertEquals(List.of("外部 API 调用：需要保持既有错误码兼容"), review.getRisks());
        assertEquals(1, review.getQuestions().size());
    }

    @Test
    void remainsUsefulForNonStandardHistoricalPlan() {
        PlanWorkspaceDTO.PlanReview review = parser.parse("""
                修复搜索页的空状态。

                ## 方案设计
                - 修改 `src/SearchPage.tsx` 的空状态组件。
                """);

        assertTrue(review.getSummary().contains("修复搜索页"));
        assertEquals(1, review.getChanges().size());
    }
}
