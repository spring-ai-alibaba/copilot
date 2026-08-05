package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGraphServiceImplTest {

    @Test
    void onlyReportsAvailableForAnIndexedWorkspace(@TempDir Path workspace) throws Exception {
        AppProperties properties = new AppProperties();
        CodeGraphServiceImpl service = new CodeGraphServiceImpl(properties);

        assertFalse(service.isAvailable(workspace));
        Files.createDirectories(workspace.resolve(".codegraph"));
        assertTrue(service.isAvailable(workspace));

        properties.getCodeGraph().setEnabled(false);
        assertFalse(service.isAvailable(workspace));
    }

    @Test
    void passesUntrustedSearchTextAsOneProcessArgument(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve(".codegraph"));
        Path executable = workspace.resolve("fake-codegraph.sh");
        Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        executable.toFile().setExecutable(true);

        AppProperties properties = new AppProperties();
        properties.getCodeGraph().setExecutable(executable.toString());
        CodeGraphServiceImpl service = new CodeGraphServiceImpl(properties);
        String query = "PlanWorkspaceDTO; touch should-not-run";

        String result = service.search(workspace, query);

        assertTrue(result.contains("query"));
        assertTrue(result.contains(query));
        assertFalse(Files.exists(workspace.resolve("should-not-run")));
    }

    @Test
    void rejectsPathsOutsideTheWorkspace(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve(".codegraph"));
        AppProperties properties = new AppProperties();
        CodeGraphServiceImpl service = new CodeGraphServiceImpl(properties);

        assertTrue(service.affectedTests(workspace, "../pom.xml").contains("参数不能为空"));
    }
}
