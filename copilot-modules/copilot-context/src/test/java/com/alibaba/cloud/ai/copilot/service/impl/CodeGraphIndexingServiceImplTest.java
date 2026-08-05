package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.service.CodeGraphIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGraphIndexingServiceImplTest {

    @Test
    void indexesAConversationCodeWorkspaceInBackground(@TempDir Path tempDir) throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path conversationWorkspace = workspaceRoot.resolve("conversation-1");
        Files.createDirectories(conversationWorkspace.resolve("src"));
        Files.writeString(conversationWorkspace.resolve("src/App.ts"), "export const app = true;");
        Path executable = tempDir.resolve("fake-codegraph.sh");
        Files.writeString(executable, "#!/bin/sh\nif [ \"$1\" = \"init\" ]; then mkdir -p \"$2/.codegraph\"; fi\nexit 0\n");
        executable.toFile().setExecutable(true);

        AppProperties properties = propertiesFor(workspaceRoot, executable);
        CodeGraphIndexingServiceImpl service = new CodeGraphIndexingServiceImpl(properties);

        CodeGraphIndexingService.IndexStatus status = service.requestIndex(conversationWorkspace);

        assertTrue(status == CodeGraphIndexingService.IndexStatus.INDEXING
                || status == CodeGraphIndexingService.IndexStatus.AVAILABLE);
        waitFor(() -> Files.isDirectory(conversationWorkspace.resolve(".codegraph")));
        assertTrue(Files.isDirectory(conversationWorkspace.resolve(".codegraph")));
    }

    @Test
    void doesNotIndexPathsOutsideTheConfiguredWorkspace(@TempDir Path tempDir) throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("App.ts"), "export const app = true;");

        AppProperties properties = propertiesFor(workspaceRoot, tempDir.resolve("missing-codegraph"));
        CodeGraphIndexingServiceImpl service = new CodeGraphIndexingServiceImpl(properties);

        assertEquals(CodeGraphIndexingService.IndexStatus.NOT_A_CODE_PROJECT, service.requestIndex(outside));
    }

    @Test
    void synchronizesAnExistingIndexAfterExecution(@TempDir Path tempDir) throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path conversationWorkspace = workspaceRoot.resolve("conversation-1");
        Files.createDirectories(conversationWorkspace.resolve(".codegraph"));
        Files.writeString(conversationWorkspace.resolve("App.ts"), "export const app = true;");
        Path executable = tempDir.resolve("fake-codegraph.sh");
        Files.writeString(executable,
                "#!/bin/sh\nprintf '%s' \"$1\" > \"$2/last-operation\"\nexit 0\n");
        executable.toFile().setExecutable(true);

        CodeGraphIndexingServiceImpl service = new CodeGraphIndexingServiceImpl(
                propertiesFor(workspaceRoot, executable));

        assertEquals(CodeGraphIndexingService.IndexStatus.SYNCING,
                service.requestSync(conversationWorkspace));
        waitFor(() -> Files.isRegularFile(conversationWorkspace.resolve("last-operation")));
        assertEquals("sync", Files.readString(conversationWorkspace.resolve("last-operation")));
    }

    private AppProperties propertiesFor(Path workspaceRoot, Path executable) {
        AppProperties properties = new AppProperties();
        properties.getWorkspace().setRootDirectory(workspaceRoot.toString());
        properties.getCodeGraph().setExecutable(executable.toString());
        properties.getCodeGraph().setIndexTimeoutSeconds(5);
        return properties;
    }

    private void waitFor(Check check) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!check.matches() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(check.matches(), "后台 CodeGraph 索引未在预期时间内完成");
    }

    @FunctionalInterface
    private interface Check {
        boolean matches() throws Exception;
    }
}
