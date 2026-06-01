package com.felix.miraagent.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.felix.miraagent.tools.ToolExecutionResult;
import com.felix.miraagent.tools.ToolStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode args(String key, String val) {
        ObjectNode n = mapper.createObjectNode();
        n.put(key, val);
        return n;
    }

    private ObjectNode args(String k1, String v1, String k2, String v2) {
        ObjectNode n = mapper.createObjectNode();
        n.put(k1, v1);
        n.put(k2, v2);
        return n;
    }

    @Test
    void writeThenReadDocxRoundTrips(@TempDir Path dir) {
        DocumentWriteToolHandler write = new DocumentWriteToolHandler(dir.toString());
        DocumentReadToolHandler read = new DocumentReadToolHandler(dir.toString());

        ToolExecutionResult w = write.execute("c1",
                args("path", "report.docx", "content", "# Title\nHello world\n- point one"));
        assertEquals(ToolStatus.SUCCESS, w.getStatus());
        assertTrue(Files.exists(dir.resolve("report.docx")));

        ToolExecutionResult r = read.execute("c2", args("path", "report.docx"));
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertTrue(r.getModelVisibleContent().contains("Title"), "应能读回标题");
        assertTrue(r.getModelVisibleContent().contains("Hello world"), "应能读回正文");
    }

    @Test
    void writeXlsxFromCsvAndReadBack(@TempDir Path dir) {
        DocumentWriteToolHandler write = new DocumentWriteToolHandler(dir.toString());
        DocumentReadToolHandler read = new DocumentReadToolHandler(dir.toString());

        ToolExecutionResult w = write.execute("c1",
                args("path", "data.xlsx", "content", "name,score\nAlice,95\n\"Bob, Jr\",80"));
        assertEquals(ToolStatus.SUCCESS, w.getStatus());

        ToolExecutionResult r = read.execute("c2", args("path", "data.xlsx"));
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertTrue(r.getModelVisibleContent().contains("Alice"));
        assertTrue(r.getModelVisibleContent().contains("Bob, Jr"), "带逗号的引号字段应保持完整");
    }

    @Test
    void writeRejectsUnsupportedExtension(@TempDir Path dir) {
        DocumentWriteToolHandler write = new DocumentWriteToolHandler(dir.toString());
        ToolExecutionResult w = write.execute("c1", args("path", "evil.exe", "content", "x"));
        assertEquals(ToolStatus.ERROR, w.getStatus());
    }

    @Test
    void readRejectsPathTraversal(@TempDir Path dir) {
        DocumentReadToolHandler read = new DocumentReadToolHandler(dir.toString());
        ToolExecutionResult r = read.execute("c1", args("path", "../../etc/passwd"));
        assertEquals(ToolStatus.ERROR, r.getStatus());
    }

    @Test
    void listShowsWrittenFiles(@TempDir Path dir) {
        new DocumentWriteToolHandler(dir.toString())
                .execute("c1", args("path", "notes.md", "content", "hi"));
        DocumentListToolHandler list = new DocumentListToolHandler(dir.toString());
        ToolExecutionResult r = list.execute("c2", mapper.createObjectNode());
        assertEquals(ToolStatus.SUCCESS, r.getStatus());
        assertTrue(r.getModelVisibleContent().contains("notes.md"));
        assertFalse(r.getModelVisibleContent().contains("[workspace is empty]"));
    }
}
