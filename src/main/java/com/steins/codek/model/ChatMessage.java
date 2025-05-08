package com.steins.codek.model;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * 表示API中的单条消息，包含角色、内容和可选的工具调用信息。
 * @author 0027013824
 */
public class ChatMessage {
    /**
     * 消息的角色，可能的值为："system", "user", "assistant", "tool"。
     */
    private final String role;
    
    /**
     * 消息的内容。
     */
    private final String content;

    // 新增字段，用于工具调用
    @Nullable
    private List<ToolCall> toolCalls; // 助手发起的工具调用列表
    @Nullable
    private String toolCallId;      // 工具结果对应的调用ID

    /**
     * 构造函数。
     * @param role 消息的角色，例如："system", "user", "assistant", "tool"。
     * @param content 消息的内容。
     */
    public ChatMessage(String role, @Nullable String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 获取消息角色。
     * @return 消息角色。
     */
    public String getRole() {
        return role;
    }

    /**
     * 获取消息内容。
     * @return 消息内容。
     */
    @Nullable
    public String getContent() {
        return content;
    }

    // --- 工具调用相关 Getter 和 Setter ---

    @Nullable
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(@Nullable List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    @Nullable
    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(@Nullable String toolCallId) {
        this.toolCallId = toolCallId;
    }
    
    // --- 内部类：表示一个工具调用请求 --- 
    
    /**
     * 表示助手消息中请求的一个具体的工具调用。
     */
    public static class ToolCall {
        private String id;           // 工具调用的唯一ID
        private String type = "function"; // 目前通常是 "function"
        private Function function;   // 包含函数名称和参数

        public ToolCall(String id, String functionName, String arguments) {
            this.id = id;
            this.function = new Function(functionName, arguments);
        }

        // Getters (和可选的 Setters)
        public String getId() { return id; }
        public String getType() { return type; }
        public Function getFunction() { return function; }
        
        // Setters 如果需要修改的话
        // public void setId(String id) { this.id = id; }
        // public void setType(String type) { this.type = type; }
        // public void setFunction(Function function) { this.function = function; }
    }
    
    /**
     * 表示 ToolCall 中的具体函数信息。
     */
    public static class Function {
        private String name;      // 要调用的函数/工具名称
        private String arguments; // 函数/工具参数的 JSON 字符串

        public Function(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        // Getters (和可选的 Setters)
        public String getName() { return name; }
        public String getArguments() { return arguments; }
        
        // public void setName(String name) { this.name = name; }
        // public void setArguments(String arguments) { this.arguments = arguments; }
    }
} 