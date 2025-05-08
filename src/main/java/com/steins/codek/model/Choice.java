package com.steins.codek.model;

/**
 * 表示OpenAI API响应中的选择项，包含模型生成的消息。
 * @author 0027013824
 */
public class Choice {
    /**
     * 模型生成的消息。
     */
    private final ChatMessage message;
    
    /**
     * 表示生成停止的原因。
     * 可能的值包括："stop", "length", "content_filter"等。
     */
    private final String finish_reason;
    
    /**
     * 选择项的索引。
     */
    private final int index;

    /**
     * 构造函数。
     * @param message 模型生成的消息。
     * @param finish_reason 生成停止的原因。
     * @param index 选择项的索引。
     */
    public Choice(ChatMessage message, String finish_reason, int index) {
        this.message = message;
        this.finish_reason = finish_reason;
        this.index = index;
    }

    /**
     * 获取模型生成的消息。
     * @return 消息对象。
     */
    public ChatMessage getMessage() {
        return message;
    }

    /**
     * 获取生成停止的原因。
     * @return 停止原因。
     */
    public String getFinish_reason() {
        return finish_reason;
    }

    /**
     * 获取选择项的索引。
     * @return 索引值。
     */
    public int getIndex() {
        return index;
    }
} 