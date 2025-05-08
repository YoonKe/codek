package com.steins.codek.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 表示一个完整的聊天会话，包含多条消息和会话元数据。
 * @author 0027013824
 */
public class ChatSession {
    private final String id;
    private String title;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<ChatMessage> messages;
    private String model;

    /**
     * 构造函数，创建一个新的聊天会话。
     * @param title 会话标题
     * @param model 使用的模型
     */
    public ChatSession(String title, String model) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.messages = new ArrayList<>();
        this.model = model;
        
        // 添加初始系统消息
        addMessage(new ChatMessage("system", "你是由Codek开发的编程助手，帮助用户解决编程问题。" +
                "请提供简洁、准确的回答，并在适当时提供代码示例。"));
    }
    
    /**
     * 向会话添加一条消息。
     * @param message 消息
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        this.updatedAt = LocalDateTime.now();
        
        // 如果没有标题且这是第一条用户消息，则使用它作为标题
        if ((this.title == null || this.title.isEmpty() || "新对话".equals(this.title)) 
                && "user".equals(message.getRole()) && getMessagesCount() <= 3) {
            String content = message.getContent();
            this.title = content.length() > 30 ? content.substring(0, 30) + "..." : content;
        }
    }
    
    /**
     * 获取会话的所有消息。
     * @return 消息列表
     */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * 获取会话的消息数量。
     * @return 消息数量
     */
    public int getMessagesCount() {
        return messages.size();
    }
    
    /**
     * 获取会话ID。
     * @return 会话ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取会话标题。
     * @return 会话标题
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * 设置会话标题。
     * @param title 新标题
     */
    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取会话创建时间。
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * 获取会话最后更新时间。
     * @return 最后更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * 获取会话使用的模型。
     * @return 模型名称
     */
    public String getModel() {
        return model;
    }
    
    /**
     * 设置会话使用的模型。
     * @param model 模型名称
     */
    public void setModel(String model) {
        this.model = model;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取会话的简短描述，包含标题和日期。
     * @return 会话描述
     */
    public String getDisplayDescription() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return title + " · " + createdAt.format(formatter);
    }
    
    /**
     * 清除会话中的所有消息，但保留系统提示。
     */
    public void clearMessages() {
        ChatMessage systemMessage = null;
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                systemMessage = message;
                break;
            }
        }
        
        messages.clear();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
        this.updatedAt = LocalDateTime.now();
        this.title = "新对话";
    }
} 