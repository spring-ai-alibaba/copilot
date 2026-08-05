package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Plan Mode 约定的 PLAN.md 转换为适合审批界面展示的结构化摘要。
 *
 * <p>解析故意保持宽容：历史会话或模型未严格遵循模板时，前端仍可回退到原始 Markdown。</p>
 */
@Component
public class PlanReviewSummaryParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "^\\s*(?:[-*]|\\d+[.)])\\s+(?:\\[[ xX]\\]\\s*)?(.*\\S)\\s*$");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*[-*]?\\s*([^：:]+)[：:]\\s*(.+?)\\s*$");
    private static final Pattern FILE_PATTERN = Pattern.compile("`([^`\\n]+\\.[A-Za-z0-9_-]+(?::\\d+(?:-\\d+)?)?)`");

    public PlanWorkspaceDTO.PlanReview parse(String planContent) {
        PlanWorkspaceDTO.PlanReview review = new PlanWorkspaceDTO.PlanReview();
        if (planContent == null || planContent.isBlank()) {
            return review;
        }

        String taskUnderstanding = section(planContent, "任务理解");
        String design = section(planContent, "方案设计");
        String changes = section(planContent, "变更清单");
        String tests = section(planContent, "测试策略");
        String risks = section(planContent, "风险点");

        review.setSummary(firstField(taskUnderstanding, "要解决的问题", "任务", "目标"));
        if (isBlank(review.getSummary())) {
            review.setSummary(firstMeaningfulLine(taskUnderstanding, planContent, design));
        }
        review.setScopeOut(valuesForField(taskUnderstanding, "不碰的范围", "不修改", "不涉及"));
        review.setChanges(parseChanges(changes, design));
        review.setVerifications(parseVerifications(tests));
        review.setRisks(parseRisks(risks));
        review.setQuestions(parseQuestions(planContent));
        return review;
    }

    private List<PlanWorkspaceDTO.PlanChange> parseChanges(String changes, String design) {
        List<PlanWorkspaceDTO.PlanChange> result = new ArrayList<>();
        List<String> header = List.of();
        for (String line : changes.lines().toList()) {
            if (!line.strip().startsWith("|") || line.matches("^\\s*\\|?\\s*-+.*")) {
                continue;
            }
            List<String> cells = Arrays.stream(line.split("\\|", -1))
                    .map(String::strip)
                    .filter(cell -> !cell.isEmpty())
                    .toList();
            if (cells.size() < 2) {
                continue;
            }
            if (isHeaderRow(cells)) {
                header = cells;
                continue;
            }
            PlanWorkspaceDTO.PlanChange change = new PlanWorkspaceDTO.PlanChange();
            String fileCell = valueAt(cells, headerIndex(header, "文件", "路径"), 0).replace("`", "");
            change.setFiles(extractFiles(fileCell));
            if (change.getFiles().isEmpty() && !fileCell.isBlank()) {
                change.setFiles(List.of(fileCell));
            }
            String symbols = valueAt(cells, headerIndex(header, "关键符号", "符号", "类", "方法", "函数"), -1);
            change.setSymbols(extractSymbols(symbols));
            change.setAction(valueAt(cells, headerIndex(header, "操作", "改动"), 1));
            change.setImpact(valueAt(cells, headerIndex(header, "影响范围", "影响"), 2));
            change.setTitle(titleFor(change));
            result.add(change);
        }
        if (!result.isEmpty()) {
            return result;
        }

        for (String bullet : bullets(changes.isBlank() ? design : changes)) {
            PlanWorkspaceDTO.PlanChange change = new PlanWorkspaceDTO.PlanChange();
            change.setTitle(bullet);
            change.setFiles(extractFiles(bullet));
            result.add(change);
        }
        return result;
    }

    private List<PlanWorkspaceDTO.PlanVerification> parseVerifications(String tests) {
        List<PlanWorkspaceDTO.PlanVerification> result = new ArrayList<>();
        String currentType = "验证";
        for (String rawLine : tests.lines().toList()) {
            Matcher field = FIELD_PATTERN.matcher(rawLine);
            if (field.matches()) {
                currentType = field.group(1).strip();
                addVerification(result, currentType, field.group(2).strip());
                continue;
            }
            Matcher bullet = BULLET_PATTERN.matcher(rawLine);
            if (bullet.matches()) {
                addVerification(result, currentType, bullet.group(1).strip());
            }
        }
        return result;
    }

    private void addVerification(
            List<PlanWorkspaceDTO.PlanVerification> result, String type, String description) {
        if (isBlank(description) || "无".equals(description)) {
            return;
        }
        PlanWorkspaceDTO.PlanVerification verification = new PlanWorkspaceDTO.PlanVerification();
        verification.setType(type);
        verification.setDescription(description);
        if (description.matches(".*`(?:mvn|gradle|npm|pnpm|yarn|pytest|go test)[^`]*`.*")) {
            Matcher command = Pattern.compile("`([^`]+)`").matcher(description);
            if (command.find()) {
                verification.setCommand(command.group(1));
            }
        }
        verification.setExpectedResult("通过并满足上述验证说明");
        result.add(verification);
    }

    private List<String> parseRisks(String risks) {
        List<String> result = new ArrayList<>();
        for (String line : risks.lines().toList()) {
            if (line.matches("^\\s*[-*]\\s*\\[[xX]\\].*")) {
                result.add(line.replaceFirst("^\\s*[-*]\\s*\\[[xX]\\]\\s*", "").strip());
            }
        }
        return result;
    }

    private List<PlanWorkspaceDTO.PlanQuestion> parseQuestions(String planContent) {
        String questions = section(planContent, "待确认", "需要确认", "开放问题");
        List<PlanWorkspaceDTO.PlanQuestion> result = new ArrayList<>();
        for (String question : bullets(questions)) {
            PlanWorkspaceDTO.PlanQuestion item = new PlanWorkspaceDTO.PlanQuestion();
            item.setQuestion(question);
            item.setBlocking(true);
            result.add(item);
        }
        return result;
    }

    private String section(String source, String... titles) {
        Matcher matcher = HEADING_PATTERN.matcher(source);
        while (matcher.find()) {
            String title = matcher.group(1).strip();
            boolean matches = Arrays.stream(titles).anyMatch(title::contains);
            if (!matches) {
                continue;
            }
            int start = matcher.end();
            if (matcher.find()) {
                return source.substring(start, matcher.start()).strip();
            }
            return source.substring(start).strip();
        }
        return "";
    }

    private String firstField(String section, String... names) {
        for (String line : section.lines().toList()) {
            Matcher matcher = FIELD_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String label = matcher.group(1).replace("-", "").strip();
            if (Arrays.stream(names).anyMatch(label::contains)) {
                return matcher.group(2).strip();
            }
        }
        return "";
    }

    private List<String> valuesForField(String section, String... names) {
        String value = firstField(section, names);
        if (isBlank(value) || "无".equals(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[、，,；;]"))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> bullets(String source) {
        List<String> result = new ArrayList<>();
        for (String line : source.lines().toList()) {
            Matcher matcher = BULLET_PATTERN.matcher(line);
            if (matcher.matches() && !"无".equals(matcher.group(1).strip())) {
                result.add(matcher.group(1).strip());
            }
        }
        return result;
    }

    private List<String> extractFiles(String source) {
        List<String> files = new ArrayList<>();
        Matcher matcher = FILE_PATTERN.matcher(source);
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        return files;
    }

    private List<String> extractSymbols(String source) {
        if (isBlank(source) || "无".equals(source.strip())) {
            return List.of();
        }
        return Arrays.stream(source.replace("`", "").split("[、，,；;]|\\s*(?:→|->)\\s*"))
                .map(String::strip)
                .filter(symbol -> !symbol.isBlank())
                .filter(symbol -> !symbol.contains("/"))
                .toList();
    }

    private String titleFor(PlanWorkspaceDTO.PlanChange change) {
        String files = change.getFiles().isEmpty() ? "代码改动" : String.join("、", change.getFiles());
        return isBlank(change.getAction()) ? files : change.getAction() + " · " + files;
    }

    private boolean isHeaderRow(List<String> cells) {
        return cells.stream().map(this::normalizeHeader).anyMatch(cell ->
                cell.contains("文件") || cell.contains("path") || cell.contains("操作"));
    }

    private int headerIndex(List<String> header, String... labels) {
        for (int index = 0; index < header.size(); index++) {
            String cell = normalizeHeader(header.get(index));
            if (Arrays.stream(labels).anyMatch(cell::contains)) {
                return index;
            }
        }
        return -1;
    }

    private String valueAt(List<String> cells, int preferredIndex, int fallbackIndex) {
        int index = preferredIndex >= 0 ? preferredIndex : fallbackIndex;
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    private String normalizeHeader(String value) {
        return value.replace("`", "").toLowerCase(Locale.ROOT).strip();
    }

    private String firstMeaningfulLine(String... sections) {
        for (String section : sections) {
            for (String line : section.lines().toList()) {
                String value = line.replaceFirst("^\\s*[-*]\\s*", "").strip();
                if (!value.isBlank() && !value.startsWith("|") && !value.startsWith("#")) {
                    return value;
                }
            }
        }
        return "计划已生成，等待你的审批";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
