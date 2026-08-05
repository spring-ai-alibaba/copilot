package com.alibaba.cloud.ai.copilot.domain.schema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchemaValidatorTest {

    @Test
    void keepsAgentScopeToolValidatorRuntimeCompatible() {
        assertDoesNotThrow(
                () -> Class.forName("io.agentscope.core.tool.ToolValidator"));
    }

    @Test
    void validatesRequiredToolArgumentsWithNetworkntV2() {
        SchemaValidator validator = new SchemaValidator();
        JsonSchema schema = JsonSchema.object()
                .addProperty("path", JsonSchema.string("File path"))
                .required("path");

        assertNull(validator.validate(schema, Map.of("path", "src/App.tsx")));
        assertNotNull(validator.validate(schema, Map.of()));
    }
}
