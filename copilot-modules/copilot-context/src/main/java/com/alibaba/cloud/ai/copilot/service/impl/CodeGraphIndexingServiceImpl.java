package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.service.CodeGraphIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话工作区的后台 CodeGraph 索引器。
 *
 * <p>索引进程由服务端固定为 {@code codegraph init/sync <conversation-workspace>}；
 * 工作区必须位于 {@code app.workspace.root-directory} 下。这样可以让用户的代码工作区
 * 自然获得 CodeGraph 能力，同时不把写索引或执行命令的权限交给模型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGraphIndexingServiceImpl implements CodeGraphIndexingService {

    private static final int MAX_INDEX_OUTPUT_BYTES = 8 * 1024;
    private static final int MAX_SCAN_DEPTH = 8;
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".scala", ".go", ".rs", ".py", ".js", ".jsx",
            ".ts", ".tsx", ".vue", ".svelte", ".html", ".css", ".cs", ".c",
            ".cc", ".cpp", ".h", ".hpp", ".php", ".rb", ".swift");
    private static final Set<String> PROJECT_MANIFESTS = Set.of(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "pyproject.toml", "requirements.txt", "go.mod", "cargo.toml",
            "composer.json", "gemfile");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".codegraph", ".git", "node_modules", "target", "dist", "build", "plans");

    private final AppProperties appProperties;
    private final ConcurrentHashMap<Path, IndexStatus> statuses = new ConcurrentHashMap<>();
    private final Set<Path> activeWorkspaces = ConcurrentHashMap.newKeySet();

    @Override
    public IndexStatus requestIndex(Path workspace) {
        return request(workspace, false);
    }

    @Override
    public IndexStatus requestSync(Path workspace) {
        return request(workspace, true);
    }

    private IndexStatus request(Path workspace, boolean preferSync) {
        Path root = normalizeConversationWorkspace(workspace);
        if (!appProperties.getCodeGraph().isEnabled() || !appProperties.getCodeGraph().isAutoIndex()) {
            return IndexStatus.DISABLED;
        }
        if (root == null || !isCodeProject(root)) {
            return IndexStatus.NOT_A_CODE_PROJECT;
        }
        boolean indexed = Files.isDirectory(root.resolve(".codegraph"));
        if (indexed && !preferSync) {
            statuses.put(root, IndexStatus.AVAILABLE);
            return IndexStatus.AVAILABLE;
        }
        if (!activeWorkspaces.add(root)) {
            return statuses.getOrDefault(root, indexed ? IndexStatus.SYNCING : IndexStatus.INDEXING);
        }

        IndexStatus pending = indexed && preferSync ? IndexStatus.SYNCING : IndexStatus.INDEXING;
        statuses.put(root, pending);
        CompletableFuture.runAsync(() -> index(root, indexed && preferSync))
                .whenComplete((ignored, error) -> activeWorkspaces.remove(root));
        return pending;
    }

    private void index(Path workspace, boolean sync) {
        String operation = sync ? "sync" : "init";
        try {
            Process process = new ProcessBuilder(
                    appProperties.getCodeGraph().getExecutable(), operation, workspace.toString())
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .start();
            Duration timeout = Duration.ofSeconds(
                    Math.max(5, appProperties.getCodeGraph().getIndexTimeoutSeconds()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                statuses.put(workspace, IndexStatus.FAILED);
                log.warn("CodeGraph {} 超时: workspace={}", operation, workspace);
                return;
            }
            byte[] output = process.getInputStream().readNBytes(MAX_INDEX_OUTPUT_BYTES);
            if (process.exitValue() == 0 && Files.isDirectory(workspace.resolve(".codegraph"))) {
                statuses.put(workspace, IndexStatus.AVAILABLE);
                log.info("CodeGraph {} 完成: workspace={}", operation, workspace);
            } else {
                statuses.put(workspace, IndexStatus.FAILED);
                log.warn("CodeGraph {} 失败: workspace={}, output={}", operation, workspace,
                        new String(output, StandardCharsets.UTF_8).strip());
            }
        } catch (IOException e) {
            statuses.put(workspace, IndexStatus.FAILED);
            log.warn("CodeGraph CLI 不可执行，跳过自动索引: workspace={}", workspace);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            statuses.put(workspace, IndexStatus.FAILED);
        } catch (Exception e) {
            statuses.put(workspace, IndexStatus.FAILED);
            log.warn("CodeGraph 自动索引失败: workspace={}", workspace, e);
        }
    }

    private Path normalizeConversationWorkspace(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return null;
        }
        Path root = workspace.toAbsolutePath().normalize();
        Path workspaceBase = Path.of(appProperties.getWorkspace().getRootDirectory())
                .toAbsolutePath().normalize();
        return root.startsWith(workspaceBase) && !root.equals(workspaceBase) ? root : null;
    }

    private boolean isCodeProject(Path root) {
        try {
            final boolean[] found = {false};
            Files.walkFileTree(root, Set.of(), MAX_SCAN_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && IGNORED_DIRECTORIES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase();
                    if (PROJECT_MANIFESTS.contains(name)
                            || SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
                        found[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return found[0];
        } catch (IOException e) {
            log.debug("无法扫描 CodeGraph 工作区: {}", root, e);
            return false;
        }
    }
}
