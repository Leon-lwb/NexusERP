package com.nexus.ai.mcp.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/** 仅供离线兼容测试的 Java STDIO 服务端；标准输出只写 UTF-8 JSON-RPC，所有入站消息写临时日志。 */
public final class JavaStdioMcpFixture {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JavaStdioMcpFixture() {
    }

    public static void main(String[] args) throws Exception {
        Path journal = Path.of(args[0]);
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             var output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                JsonNode request = JSON.readTree(line);
                Files.writeString(journal, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                Map<String, Object> response = responseFor(request);
                if (response != null) {
                    output.write(JSON.writeValueAsString(response));
                    output.newLine();
                    output.flush();
                }
            }
        }
    }

    /** HTTP 与 STDIO 共用同一最小旧版协议；不返回慢调用结果，以验证 SDK 的超时及取消。 */
    static Map<String, Object> responseFor(JsonNode request) {
        String method = request.path("method").asText();
        if (!request.has("id")) {
            return null;
        }
        long id = request.path("id").asLong();
        Object result;
        switch (method) {
            case "initialize":
                result = Map.of(
                    "protocolVersion", request.path("params").path("protocolVersion").asText(),
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "nexus-offline-java-fixture", "version", "1.0"));
                break;
            case "tools/list":
                result = Map.of("tools", List.of(Map.of(
                    "name", "echo", "description", "中文物料查询",
                    "inputSchema", Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string", "description", "物料与批次")),
                        "required", List.of("message")))));
                break;
            case "tools/call":
                String name = request.path("params").path("name").asText();
                if ("slow".equals(name)) {
                    return null;
                }
                boolean failed = "fail".equals(name);
                String text = failed ? "物料不存在：请核对编码" : "收到：" + request.path("params").path("arguments").path("message").asText();
                result = Map.of("isError", failed, "content", List.of(Map.of("type", "text", "text", text)));
                break;
            case "ping":
                result = Map.of();
                break;
            default:
                return Map.of("jsonrpc", "2.0", "id", id,
                    "error", Map.of("code", -32601, "message", "Method not found"));
        }
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }
}
