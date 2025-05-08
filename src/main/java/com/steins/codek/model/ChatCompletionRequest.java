package com.steins.codek.model;

import java.util.List;

/**
 * 表示发送给OpenAI聊天补全API的请求体。
 * @author 0027013824
 */
public class ChatCompletionRequest {
    /**
     * 要使用的模型ID，例如："gpt-4o", "gpt-3.5-turbo"。
     */
    private final String model;
    
    /**
     * 对话历史消息列表。
     */
    private final List<ChatMessage> messages;
    
    /**
     * 温度参数，控制回复的随机性。
     * 值越低，回复越确定性；值越高，回复越多样化。
     * 范围为0-2，默认为1。
     */
    private final double temperature;
    
    /**
     * 是否开启流式输出。
     */
    private final boolean stream;

    /**
     * 创建一个新的聊天完成请求。
     * @param model 要使用的模型ID。
     * @param messages 对话历史消息列表。
     * @param temperature 温度参数，控制随机性。
     * @param stream 是否开启流式输出。
     */
    public ChatCompletionRequest(String model, List<ChatMessage> messages, double temperature, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.stream = stream;
    }

    /**
     * 获取模型ID。
     * @return 模型ID。
     */
    public String getModel() {
        return model;
    }

    /**
     * 获取消息列表。
     * @return 消息列表。
     */
    public List<ChatMessage> getMessages() {
        return messages;
    }

    /**
     * 获取温度参数。
     * @return 温度参数。
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * 是否开启流式输出。
     * @return 是否开启流式输出。
     */
    public boolean isStream() {
        return stream;
    }
} 