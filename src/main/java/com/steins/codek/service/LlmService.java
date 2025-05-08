package com.steins.codek.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.steins.codek.model.ChatMessage;
import com.steins.codek.tool.Tool;
import com.steins.codek.tool.ToolParameter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LLM服务类，封装与大模型 API的交互逻辑。
 * 支持工具调用 (Function Calling)。
 *
 * @author 0027013824
 */
public class LlmService {
    private static final Logger LOG = Logger.getInstance(LlmService.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 120; // 增加超时时间以应对可能较长的工具执行
    
    private final OkHttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final String model;
    private final String apiUrl; // API地址
    private final ToolExecutor toolExecutor; // 添加 ToolExecutor
    private final List<Tool> availableTools; // 存储可用工具列表
    
    /**
     * 构造函数。
     *
     * @param apiKey  API密钥。
     * @param model   使用的模型。
     * @param apiUrl  API的URL地址。
     * @param project 当前项目，用于初始化 ToolExecutor。
     */
    public LlmService(String apiKey, String model, String apiUrl, @NotNull Project project) {
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.toolExecutor = new ToolExecutor(project);
        this.availableTools = toolExecutor.getAvailableTools();
    }
    
    /**
     * 获取当前使用的模型。
     *
     * @return 模型名称。
     */
    public String getModel() {
        return model;
    }
    
    /**
     * 发送聊天消息到大模型 API 并以流式方式处理响应。
     * 支持工具调用 (Function Calling)。
     */
    public void streamChatCompletion(List<ChatMessage> messages, double temperature, StreamingCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            handleError(callback, new IOException("API密钥未配置"));
            return;
        }
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            handleError(callback, new IOException("API地址未配置"));
            return;
        }
        
        try {
            JsonObject requestBody = buildRequestBody(messages, temperature, true);
            if (!availableTools.isEmpty()) {
                requestBody.add("tools", buildToolsJson());
                requestBody.addProperty("tool_choice", "auto");
            }
            Request request = buildRequest(requestBody);
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    LOG.warn("API 流式请求失败: " + e.getMessage(), e);
                    handleError(callback, e);
                }
                
                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            String errorBody = responseBody != null ? responseBody.string() : "未知错误";
                            handleError(callback, new IOException("API响应错误: " + response.code() + ", " + errorBody));
                            return;
                        }
                        if (responseBody == null) {
                            handleError(callback, new IOException("空响应体"));
                            return;
                        }
                        processStream(responseBody, messages, temperature, callback);
                    }
                    catch (IOException e) {
                        LOG.error("处理 API 响应流时出错", e);
                        handleError(callback, e);
                    }
                    catch (Exception e) {
                        LOG.error("处理 API 响应时发生意外错误", e);
                        handleError(callback, e);
                    }
                }
            });
        }
        catch (Exception e) {
            LOG.error("准备 API 流式请求时出错", e);
            handleError(callback, e);
        }
    }
    
    private JsonObject buildRequestBody(List<ChatMessage> messages, double temperature, boolean stream) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", this.model);
        requestBody.addProperty("temperature", temperature);
        if (stream) {
            requestBody.addProperty("stream", true);
        }
        requestBody.add("messages", buildMessagesJson(messages));
        return requestBody;
    }
    
    private JsonArray buildMessagesJson(List<ChatMessage> messages) {
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("role", message.getRole());
            
            List<ChatMessage.ToolCall> toolCalls = message.getToolCalls();
            String toolCallId = message.getToolCallId();
            
            if (toolCalls != null && !toolCalls.isEmpty()) {
                if ("assistant".equals(message.getRole())) {
                    messageObj.add("tool_calls", gson.toJsonTree(toolCalls));
                    messageObj.add("content", JsonNull.INSTANCE);
                }
                else {
                    LOG.warn("消息角色 '" + message.getRole() + "' 不应包含 tool_calls，已忽略。");
                    messageObj.addProperty("content", message.getContent());
                }
            }
            else if ("tool".equals(message.getRole())) {
                messageObj.addProperty("tool_call_id", toolCallId);
                messageObj.addProperty("content", message.getContent());
            }
            else {
                if (message.getContent() != null) {
                    messageObj.addProperty("content", message.getContent());
                }
                else if ("assistant".equals(message.getRole())) {
                    messageObj.add("content", JsonNull.INSTANCE);
                }
                else {
                    LOG.warn("消息角色 '" + message.getRole() + "' 的 content 为 null。");
                    messageObj.addProperty("content", "");
                }
            }
            messagesArray.add(messageObj);
        }
        return messagesArray;
    }
    
    private Request buildRequest(JsonObject requestBody) {
        Request.Builder builder = new Request.Builder()
                .url(this.apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");
        
        if (requestBody.has("stream") && requestBody.get("stream").getAsBoolean()) {
            builder.addHeader("Accept", "text/event-stream");
        }
        
        builder.post(RequestBody.create(requestBody.toString(), JSON));
        return builder.build();
    }
    
    private void processStream(ResponseBody responseBody, List<ChatMessage> originalMessages, double temperature, StreamingCallback callback) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
            String line;
            StringBuilder accumulatedContent = new StringBuilder();
            List<ToolCall> currentToolCalls = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String jsonData = line.substring(5).trim();
                    if (jsonData.equals("[DONE]")) {
                        break;
                    }
                    try {
                        JsonObject dataObject = gson.fromJson(jsonData, JsonObject.class);
                        if (dataObject.has("choices")) {
                            JsonArray choices = dataObject.getAsJsonArray("choices");
                            if (choices.size() > 0) {
                                JsonObject choice = choices.get(0).getAsJsonObject();
                                JsonObject delta = choice.getAsJsonObject("delta");
                                
                                if (delta != null) {
                                    if (delta.has("tool_calls")) {
                                        JsonArray toolCallsJson = delta.getAsJsonArray("tool_calls");
                                        parseToolCallChunks(toolCallsJson, currentToolCalls);
                                    }
                                    
                                    if (delta.has("content")) {
                                        JsonElement contentElement = delta.get("content");
                                        // Fix: Check if element is JsonNull before calling getAsString()
                                        if (contentElement != null && !contentElement.isJsonNull()) {
                                            if (contentElement.isJsonPrimitive() && contentElement.getAsJsonPrimitive().isString()) {
                                                String contentChunk = contentElement.getAsString();
                                                accumulatedContent.append(contentChunk);
                                                handleChunkReceived(callback, contentChunk);
                                            }
                                            else {
                                                LOG.warn("Received non-null, non-string content in delta: " + contentElement.toString());
                                            }
                                        }
                                        // If content is JsonNull, we don't need to do anything
                                    }
                                }
                                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                                    String finishReason = choice.get("finish_reason").getAsString();
                                    if ("tool_calls".equals(finishReason)) {
                                        handleToolCalls(currentToolCalls, originalMessages, temperature, callback);
                                        return;
                                    }
                                    else if ("stop".equals(finishReason)) {
                                        break;
                                    }
                                    else {
                                        LOG.warn("Stream finished with reason: " + finishReason);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    catch (JsonParseException e) {
                        LOG.warn("解析流数据时出错: " + jsonData, e);
                    }
                }
            }
            handleComplete(callback, accumulatedContent.toString());
        }
    }
    
    /**
     * 解析并合并流式工具调用块。
     */
    private void parseToolCallChunks(JsonArray toolCallsJson, List<ToolCall> currentToolCalls) {
        for (JsonElement tcElement : toolCallsJson) {
            if (!tcElement.isJsonObject()) {
                continue;
            }
            JsonObject tcObject = tcElement.getAsJsonObject();
            
            JsonElement indexElement = tcObject.get("index");
            if (indexElement == null || !indexElement.isJsonPrimitive() || !indexElement.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            int index = indexElement.getAsInt();
            
            String id = getStringField(tcObject, "id", null);
            String type = getStringField(tcObject, "type", "function");
            
            if (!tcObject.has("function") || !tcObject.get("function").isJsonObject()) {
                continue;
            }
            JsonObject functionJson = tcObject.getAsJsonObject("function");
            
            String name = getStringField(functionJson, "name", null);
            // 处理arguments时，确保它是一个字符串，然后直接使用
            String argumentsChunk = getStringField(functionJson, "arguments", "");
            
            while (currentToolCalls.size() <= index) {
                currentToolCalls.add(new ToolCall());
            }
            ToolCall currentToolCall = currentToolCalls.get(index);
            if (currentToolCall.id == null && id != null) {
                currentToolCall.id = id;
            }
            if (currentToolCall.functionName == null && name != null) {
                currentToolCall.functionName = name;
            }
            currentToolCall.appendArguments(argumentsChunk);
        }
    }
    
    private String getStringField(JsonObject obj, String fieldName, String defaultValue) {
        if (obj == null || !obj.has(fieldName)) {
            return defaultValue;
        }
        
        JsonElement element = obj.get(fieldName);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        
        // 如果不是字符串类型，返回元素的字符串表示形式
        return element.toString();
    }
    
    private void handleToolCalls(List<ToolCall> toolCalls, List<ChatMessage> currentMessages, double temperature, StreamingCallback callback) {
        List<ChatMessage> messagesForNextTurn = new ArrayList<>(currentMessages);
        
        ChatMessage assistantMessageWithCalls = new ChatMessage("assistant", null);
        List<ChatMessage.ToolCall> validToolCalls = toolCalls.stream()
                .filter(tc -> tc.id != null && tc.functionName != null)
                .map(tc -> new ChatMessage.ToolCall(tc.id, tc.functionName, tc.arguments.toString()))
                .collect(Collectors.toList());
        
        if (!validToolCalls.isEmpty()) {
            assistantMessageWithCalls.setToolCalls(validToolCalls);
            messagesForNextTurn.add(assistantMessageWithCalls);
        }
        else {
            LOG.warn("模型指示 tool_calls 结束，但未收到有效的工具调用数据。");
            handleComplete(callback, "");
            return;
        }
        
        List<CompletableFuture<ToolExecutor.ToolExecutionResult>> futures = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            if (toolCall.id == null || toolCall.functionName == null) {
                continue;
            }
            
            String argumentsStr = toolCall.arguments.toString();
            
            CompletableFuture<ToolExecutor.ToolExecutionResult> future = new CompletableFuture<>();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    ToolExecutor.ToolExecutionResult result = toolExecutor.executeToolCall(toolCall.functionName, argumentsStr);
                    result.setToolCallId(toolCall.id);
                    future.complete(result);
                }
                catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAcceptAsync(v -> {
            for (CompletableFuture<ToolExecutor.ToolExecutionResult> future : futures) {
                try {
                    ToolExecutor.ToolExecutionResult result = future.join();
                    ChatMessage toolResultMessage = new ChatMessage("tool", result.getResultForLLM());
                    toolResultMessage.setToolCallId(result.getToolCallId());
                    messagesForNextTurn.add(toolResultMessage);
                }
                catch (Exception e) {
                    LOG.error("获取工具执行结果时出错", e);
                    String failedToolCallId = "unknown";
                    ChatMessage errorMsg = new ChatMessage("tool",
                            String.format("{\"error\": \"Failed to get tool execution result: %s\"}", escapeJson(e.getMessage())));
                    errorMsg.setToolCallId(failedToolCallId);
                    messagesForNextTurn.add(errorMsg);
                }
            }
            
            LOG.info("将工具结果发送回 LLM 进行下一步处理...");
            streamChatCompletion(messagesForNextTurn, temperature, callback);
            
        }).exceptionally(e -> {
            LOG.error("执行一个或多个工具时出错", e);
            handleError(callback, new IOException("执行工具时出错: " + e.getMessage(), e));
            return null;
        });
    }
    
    private JsonArray buildToolsJson() {
        JsonArray toolsJson = new JsonArray();
        for (Tool tool : availableTools) {
            JsonObject toolObject = new JsonObject();
            toolObject.addProperty("type", "function");
            JsonObject functionObject = new JsonObject();
            functionObject.addProperty("name", tool.getName());
            functionObject.addProperty("description", tool.getDescription());
            
            JsonObject parametersObject = new JsonObject();
            parametersObject.addProperty("type", "object");
            JsonObject propertiesObject = new JsonObject();
            JsonArray requiredArray = new JsonArray();
            
            if (tool.getParameters() != null) {
                for (ToolParameter param : tool.getParameters()) {
                    JsonObject paramProps = new JsonObject();
                    String paramType = param.getType();
                    String jsonType = mapTypeToJsonSchema(paramType);
                    paramProps.addProperty("type", jsonType);
                    paramProps.addProperty("description", param.getDescription());
                    propertiesObject.add(param.getName(), paramProps);
                    if (param.isRequired()) {
                        requiredArray.add(param.getName());
                    }
                }
            }
            parametersObject.add("properties", propertiesObject);
            if (requiredArray.size() > 0) {
                parametersObject.add("required", requiredArray);
            }
            
            functionObject.add("parameters", parametersObject);
            toolObject.add("function", functionObject);
            toolsJson.add(toolObject);
        }
        return toolsJson;
    }
    
    private String mapTypeToJsonSchema(String toolType) {
        if (toolType == null) {
            return "string";
        }
        return switch (toolType.toLowerCase()) {
            case "integer", "int" -> "integer";
            case "number", "float", "double" -> "number";
            case "boolean", "bool" -> "boolean";
            case "string", "text", "file_path", "filepath" -> "string";
            default -> "string";
        };
    }
    
    private void handleChunkReceived(StreamingCallback callback, String chunk) {
        ApplicationManager.getApplication().invokeLater(() -> callback.onChunkReceived(chunk));
    }
    
    private void handleComplete(StreamingCallback callback, String finalContent) {
        ApplicationManager.getApplication().invokeLater(callback::onComplete);
    }
    
    private void handleError(StreamingCallback callback, Exception e) {
        ApplicationManager.getApplication().invokeLater(() -> callback.onError(e));
    }
    
    private static class ToolCall {
        String id;
        String functionName;
        StringBuilder arguments = new StringBuilder();
        
        void appendArguments(String chunk) {
            arguments.append(chunk);
        }
        
        @Override
        public String toString() {
            return "ToolCall{" +
                    "id='" + id + '\'' +
                    ", functionName='" + functionName + '\'' +
                    ", arguments=" + arguments +
                    '}';
        }
    }
    
    public interface StreamingCallback {
        void onChunkReceived(String textChunk);
        
        void onComplete();
        
        void onError(Exception e);
    }
    
    private static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                default:
                    if (c <= '\u001F') {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}

