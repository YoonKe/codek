package com.steins.codek.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.steins.codek.tool.Tool;
import com.steins.codek.tool.impl.ReadFileTool; // 导入我们实现的第一个工具
import com.steins.codek.tool.impl.WriteFileTool; // 导入写文件工具
import com.steins.codek.tool.impl.CreateFileTool; // 导入创建文件工具
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// 引入 JSON 解析库 (假设使用 Gson)
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 负责管理和执行 LLM 请求的工具。
 * @author 0027013824
 */
public class ToolExecutor {
    private static final Logger LOG = Logger.getInstance(ToolExecutor.class);
    private final Map<String, Tool> availableTools = new HashMap<>();
    private final Project project; // 需要 Project 来实例化某些工具
    private final Gson gson; // 用于解析 JSON 参数

    /**
     * 构造函数。
     * @param project 当前项目。
     */
    public ToolExecutor(@NotNull Project project) {
        this.project = project;
        this.gson = new Gson();
        registerTools();
    }

    /**
     * 注册所有可用的工具。
     */
    private void registerTools() {
        // 注册 ReadFileTool
        ReadFileTool readFileTool = new ReadFileTool(project);
        availableTools.put(readFileTool.getName(), readFileTool);

        // 注册 WriteFileTool
        WriteFileTool writeFileTool = new WriteFileTool(project);
        availableTools.put(writeFileTool.getName(), writeFileTool);

        // 注册 CreateFileTool
        CreateFileTool createFileTool = new CreateFileTool(project);
        availableTools.put(createFileTool.getName(), createFileTool);

        // TODO: 在此注册其他工具，例如 ListFilesTool 等
    }

    /**
     * 获取所有已注册的工具列表。
     * @return 工具列表。
     */
    @NotNull
    public List<Tool> getAvailableTools() {
        return new ArrayList<>(availableTools.values());
    }

    /**
     * 执行单个工具调用。
     * @param toolName 要执行的工具名称。
     * @param argumentsJson 工具参数的 JSON 字符串。
     * @return 工具执行结果。
     */
    @NotNull
    public ToolExecutionResult executeToolCall(@NotNull String toolName, @NotNull String argumentsJson) {
        Tool tool = availableTools.get(toolName);
        if (tool == null) {
            LOG.warn("未找到名为 '" + toolName + "' 的工具。");
            return new ToolExecutionResult(toolName, false, 
                    String.format("{\"error\": \"Tool not found: %s\"}", escapeJson(toolName)), null);
        }

        Map<String, String> arguments = null;
        try {
            // 解析 JSON 参数
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            arguments = gson.fromJson(argumentsJson, type);
            if (arguments == null) { // 处理空 JSON "{}" 或无效 JSON
                 arguments = new HashMap<>();
            }
            
            // 检查必需参数 (如果需要，可以在这里添加严格检查)
            // for (ToolParameter param : tool.getParameters()) {
            //     if (param.isRequired() && !arguments.containsKey(param.getName())) {
            //         throw new IllegalArgumentException("Missing required parameter: " + param.getName());
            //     }
            // }
            
            // 执行工具
            LOG.info("执行工具: " + toolName + "，参数: " + argumentsJson);
            String result = tool.execute(arguments); // Tool 接口的 execute 需要 Map<String, String>
            LOG.info("工具 '" + toolName + "' 执行完成。结果片段: " + 
                     (result.length() > 100 ? result.substring(0, 100) + "..." : result));
            return new ToolExecutionResult(toolName, true, result, arguments);

        } catch (JsonSyntaxException e) {
            LOG.warn("执行工具 '" + toolName + "' 时参数 JSON 解析错误: " + argumentsJson, e);
            return new ToolExecutionResult(toolName, false, 
                    String.format("{\"error\": \"Invalid JSON arguments for tool %s: %s\"}", 
                                 escapeJson(toolName), escapeJson(e.getMessage())), null);
        } catch (IllegalArgumentException e) {
            LOG.warn("执行工具 '" + toolName + "' 时参数错误: " + e.getMessage(), e);
            return new ToolExecutionResult(toolName, false, 
                    String.format("{\"error\": \"Invalid arguments for tool %s: %s\"}", 
                                 escapeJson(toolName), escapeJson(e.getMessage())), arguments);
        } catch (Exception e) {
            LOG.error("执行工具 '" + toolName + "' 时发生意外错误", e);
            return new ToolExecutionResult(toolName, false, 
                    String.format("{\"error\": \"Internal error executing tool %s: %s\"}", 
                                 escapeJson(toolName), escapeJson(e.getMessage())), arguments);
        }
    }

    /**
     * 用于封装工具执行结果的内部类。
     */
    public static class ToolExecutionResult {
        private final String toolName;
        private final boolean success;
        private final String result; // 工具返回的原始字符串 (通常是 JSON)
        private final Map<String, String> arguments; // 执行时使用的参数
        private String toolCallId; // 添加 toolCallId 以匹配 OpenAI 格式

        public ToolExecutionResult(String toolName, boolean success, @NotNull String result, @Nullable Map<String, String> arguments) {
            this.toolName = toolName;
            this.success = success;
            this.result = result;
            this.arguments = arguments;
        }

        public void setToolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
        }
        
        public String getToolCallId() {
             return toolCallId;
         }

        public String getToolName() {
            return toolName;
        }

        public boolean isSuccess() {
            return success;
        }

        @NotNull
        public String getResult() {
            return result;
        }

        @Nullable
        public Map<String, String> getArguments() {
            return arguments;
        }

        /**
         * 返回工具执行的原始结果字符串，通常是 JSON 格式。
         * LLM Service 将使用此内容构建 'tool' role 消息。
         * @return 结果字符串 (JSON)
         */
        public String getResultForLLM() {
            return result;
        }
    }
    
    /**
     * 简单的 JSON 字符串转义（用于错误消息）。
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '/': sb.append("\\/"); break;
                default:
                    if (c <= '\u001F') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}