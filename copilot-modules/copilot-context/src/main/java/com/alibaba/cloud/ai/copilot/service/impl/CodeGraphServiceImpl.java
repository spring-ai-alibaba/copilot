package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.service.CodeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CodeGraph CLI 的只读白名单适配器。
 *
 * <p>模型只能传入查询文本、符号或工作区内相对文件路径；CLI 名称、项目目录、子命令、输出大小
 * 和超时都由服务端控制。索引的 init/sync 不属于此服务，因此 Plan Agent 不能通过它写入工作区。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGraphServiceImpl implements CodeGraphService {

    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_RESULTS = 12;
    private static final int MAX_EXPLORE_FILES = 8;

    private final AppProperties appProperties;

    @Override
    public boolean isAvailable(Path workspace) {
        return appProperties.getCodeGraph().isEnabled()
                && validWorkspace(workspace)
                && Files.isDirectory(workspace.toAbsolutePath().normalize().resolve(".codegraph"));
    }

    @Override
    public String search(Path workspace, String query) {
        return execute(workspace, cleanQuery(query), List.of("query", "--json", "--limit", String.valueOf(MAX_RESULTS)));
    }

    @Override
    public String explore(Path workspace, String query) {
        return execute(workspace, cleanQuery(query), List.of("explore", "--max-files", String.valueOf(MAX_EXPLORE_FILES)));
    }

    @Override
    public String impact(Path workspace, String symbol) {
        return execute(workspace, cleanQuery(symbol), List.of("impact", "--json"));
    }

    @Override
    public String affectedTests(Path workspace, String relativePath) {
        String path = cleanRelativePath(relativePath);
        return execute(workspace, path, List.of("affected", "--json"));
    }

    private String execute(Path workspace, String subject, List<String> command) {
        if (!isAvailable(workspace)) {
            return "CodeGraph 当前不可用：工作区尚未建立索引或功能已关闭。请使用 read_file、grep_files 和 git 命令继续只读分析。";
        }
        if (subject.isBlank()) {
            return "参数不能为空。";
        }

        Path root = workspace.toAbsolutePath().normalize();
        List<String> arguments = new ArrayList<>();
        arguments.add(appProperties.getCodeGraph().getExecutable());
        arguments.add(command.getFirst());
        arguments.addAll(command.subList(1, command.size()));
        arguments.add("--path");
        arguments.add(root.toString());
        arguments.add(subject);

        try {
            Process process = new ProcessBuilder(arguments)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            int maxBytes = Math.max(1024, appProperties.getCodeGraph().getMaxOutputBytes());
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getInputStream().readNBytes(maxBytes + 1);
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            Duration timeout = Duration.ofSeconds(Math.max(1, appProperties.getCodeGraph().getTimeoutSeconds()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                return "CodeGraph 查询超时，请缩小符号或文件范围后重试。";
            }
            byte[] output = outputFuture.get(1, TimeUnit.SECONDS);
            String text = new String(output, 0, Math.min(output.length, maxBytes), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) {
                log.debug("CodeGraph 查询失败: command={}, output={}", command.getFirst(), text);
                return "CodeGraph 查询未返回结果：" + (text.isBlank() ? "请改用文件搜索继续分析。" : text);
            }
            if (output.length > maxBytes) {
                return text + "\n\n[结果已截断，请缩小查询范围]";
            }
            return text.isBlank() ? "CodeGraph 没有找到相关结果。" : text;
        } catch (IOException e) {
            log.debug("CodeGraph CLI 不可执行", e);
            return "CodeGraph CLI 不可执行，请改用文件搜索继续分析。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "CodeGraph 查询已中断。";
        } catch (Exception e) {
            log.debug("读取 CodeGraph 查询结果失败", e);
            return "CodeGraph 查询失败，请改用文件搜索继续分析。";
        }
    }

    private boolean validWorkspace(Path workspace) {
        return workspace != null && Files.isDirectory(workspace.toAbsolutePath().normalize());
    }

    private String cleanQuery(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip().replaceAll("[\\r\\n]+", " ");
        return cleaned.substring(0, Math.min(cleaned.length(), MAX_QUERY_LENGTH));
    }

    private String cleanRelativePath(String value) {
        String path = cleanQuery(value).replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.contains("..")) {
            return "";
        }
        return path;
    }
}
