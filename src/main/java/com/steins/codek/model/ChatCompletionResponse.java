package com.steins.codek.model;

import java.util.List;

/**
 * 表示从OpenAI聊天补全API收到的响应体。
 * @author 0027013824
 */
public class ChatCompletionResponse {
    /**
     * 响应的ID。
     */
    private final String id;
    
    /**
     * 响应的对象类型，通常为"chat.completion"。
     */
    private final String object;
    
    /**
     * 响应创建的时间戳。
     */
    private final long created;
    
    /**
     * 使用的模型名称。
     */
    private final String model;
    
    /**
     * 响应中包含的选择项列表。
     */
    private final List<Choice> choices;
    
    /**
     * 使用的token计数信息。
     */
    private final Usage usage;

    /**
     * 构造函数。
     * @param id 响应ID。
     * @param object 对象类型。
     * @param created 创建时间戳。
     * @param model 使用的模型。
     * @param choices 选择项列表。
     * @param usage token使用情况。
     */
    public ChatCompletionResponse(String id, String object, long created, String model, List<Choice> choices, Usage usage) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

    /**
     * 获取响应ID。
     * @return 响应ID。
     */
    public String getId() {
        return id;
    }

    /**
     * 获取对象类型。
     * @return 对象类型。
     */
    public String getObject() {
        return object;
    }

    /**
     * 获取创建时间戳。
     * @return 创建时间戳。
     */
    public long getCreated() {
        return created;
    }

    /**
     * 获取使用的模型。
     * @return 模型名称。
     */
    public String getModel() {
        return model;
    }

    /**
     * 获取选择项列表。
     * @return 选择项列表。
     */
    public List<Choice> getChoices() {
        return choices;
    }

    /**
     * 获取token使用情况。
     * @return token使用情况。
     */
    public Usage getUsage() {
        return usage;
    }

    /**
     * 获取第一个选择项中的消息内容，这通常是我们需要的响应。
     * @return 第一个选择项的消息，如果没有则返回null。
     */
    public ChatMessage getFirstChoiceMessage() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).getMessage();
        }
        return null;
    }

    /**
     * 表示token使用情况的内部类。
     */
    public static class Usage {
        /**
         * 提示使用的token数。
         */
        private final int prompt_tokens;
        
        /**
         * 完成使用的token数。
         */
        private final int completion_tokens;
        
        /**
         * 总共使用的token数。
         */
        private final int total_tokens;

        /**
         * 构造函数。
         * @param prompt_tokens 提示token数。
         * @param completion_tokens 完成token数。
         * @param total_tokens 总token数。
         */
        public Usage(int prompt_tokens, int completion_tokens, int total_tokens) {
            this.prompt_tokens = prompt_tokens;
            this.completion_tokens = completion_tokens;
            this.total_tokens = total_tokens;
        }

        /**
         * 获取提示token数。
         * @return 提示token数。
         */
        public int getPrompt_tokens() {
            return prompt_tokens;
        }

        /**
         * 获取完成token数。
         * @return 完成token数。
         */
        public int getCompletion_tokens() {
            return completion_tokens;
        }

        /**
         * 获取总token数。
         * @return 总token数。
         */
        public int getTotal_tokens() {
            return total_tokens;
        }
    }
} 