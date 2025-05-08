package com.steins.codek.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.steins.codek.config.CodekConfig;
import com.steins.codek.model.ChatMessage;
import com.steins.codek.model.ChatSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话管理器，负责创建、存储和管理聊天会话。
 * 使用PersistentStateComponent以便会话在IDE重启后仍然保留。
 * @author 0027013824
 */
@Service
@State(
    name = "com.steins.codek.service.SessionManager",
    storages = @Storage("codek-sessions.xml")
)
public final class SessionManager implements PersistentStateComponent<SessionManager.State> {
    private static final Logger LOG = Logger.getInstance(SessionManager.class);
    
    private State myState = new State();
    private ChatSession activeSession;
    
    /**
     * 获取应用级别的SessionManager实例。
     * @return SessionManager实例
     */
    public static SessionManager getInstance() {
        return ApplicationManager.getApplication().getService(SessionManager.class);
    }
    
    /**
     * 初始化SessionManager，如果没有会话则创建一个新会话。
     */
    public SessionManager() {
        if (myState.sessions.isEmpty()) {
            createNewSession();
        } else {
            // 首次启动时设置最近的会话为活动会话
            String latestSessionId = myState.sessions.entrySet().stream()
                    .sorted(Map.Entry.<String, SerializableSession>comparingByValue(
                            Comparator.comparing(s -> s.updatedAt)).reversed())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            
            if (latestSessionId != null) {
                activeSession = deserializeSession(myState.sessions.get(latestSessionId));
            } else {
                createNewSession();
            }
        }
    }
    
    /**
     * 创建一个新的聊天会话并设置为活动会话。
     * @return 新创建的会话
     */
    public ChatSession createNewSession() {
        CodekConfig config = ApplicationManager.getApplication().getService(CodekConfig.class);
        ChatSession session = new ChatSession("新对话", config.getCurrentModel());
        activeSession = session;
        
        // 将会话保存到持久化状态
        myState.sessions.put(session.getId(), serializeSession(session));
        return session;
    }
    
    /**
     * 获取当前活动的聊天会话。
     * @return 当前活动会话
     */
    public ChatSession getActiveSession() {
        return activeSession;
    }
    
    /**
     * 设置活动会话。
     * @param sessionId 要设置为活动的会话ID
     * @return 设置后的活动会话，如果ID无效则返回null
     */
    public ChatSession setActiveSession(String sessionId) {
        if (myState.sessions.containsKey(sessionId)) {
            activeSession = deserializeSession(myState.sessions.get(sessionId));
            return activeSession;
        }
        return null;
    }
    
    /**
     * 获取所有会话的列表（按更新时间降序排序）。
     * @return 会话列表
     */
    public List<ChatSession> getAllSessions() {
        return myState.sessions.values().stream()
                .map(this::deserializeSession)
                .sorted(Comparator.comparing(ChatSession::getUpdatedAt).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * 保存会话到持久化存储。
     * @param session 要保存的会话
     */
    public void saveSession(ChatSession session) {
        myState.sessions.put(session.getId(), serializeSession(session));
    }
    
    /**
     * 删除会话。
     * @param sessionId 要删除的会话ID
     * @return 如果删除成功则返回true
     */
    public boolean deleteSession(String sessionId) {
        if (myState.sessions.containsKey(sessionId)) {
            myState.sessions.remove(sessionId);
            // 如果删除的是当前活动会话，则设置另一个会话为活动
            if (activeSession != null && sessionId.equals(activeSession.getId())) {
                if (myState.sessions.isEmpty()) {
                    createNewSession();
                } else {
                    String newActiveId = myState.sessions.keySet().iterator().next();
                    setActiveSession(newActiveId);
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * 更新活动会话，例如在添加新消息后。
     */
    public void updateActiveSession() {
        if (activeSession != null) {
            saveSession(activeSession);
        }
    }
    
    /**
     * 将ChatSession对象序列化为存储状态。
     * @param session 要序列化的会话
     * @return 序列化后的会话
     */
    private SerializableSession serializeSession(ChatSession session) {
        SerializableSession serSession = new SerializableSession();
        serSession.id = session.getId();
        serSession.title = session.getTitle();
        serSession.createdAt = session.getCreatedAt().toString();
        serSession.updatedAt = session.getUpdatedAt().toString();
        serSession.model = session.getModel();
        
        for (ChatMessage message : session.getMessages()) {
            SerializableChatMessage serMessage = new SerializableChatMessage();
            serMessage.role = message.getRole();
            serMessage.content = message.getContent();
            serSession.messages.add(serMessage);
        }
        
        return serSession;
    }
    
    /**
     * 将存储状态反序列化为ChatSession对象。
     * @param serSession 序列化的会话
     * @return 反序列化后的会话对象
     */
    private ChatSession deserializeSession(SerializableSession serSession) {
        // 反序列化时不应使用构造函数中的初始系统消息，因为消息已经保存在序列化对象中
        ChatSession session = new ChatSession(serSession.title, serSession.model);
        
        // 清除构造函数自动添加的消息
        session.clearMessages();
        
        // 添加序列化的消息
        for (SerializableChatMessage serMessage : serSession.messages) {
            session.addMessage(new ChatMessage(serMessage.role, serMessage.content));
        }
        
        return session;
    }
    
    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }
    
    /**
     * 持久化状态类，用于存储会话数据。
     */
    public static class State {
        public Map<String, SerializableSession> sessions = new HashMap<>();
    }
    
    /**
     * 可序列化的会话类，用于持久化存储。
     */
    public static class SerializableSession {
        public String id;
        public String title;
        public String createdAt;
        public String updatedAt;
        public String model;
        public List<SerializableChatMessage> messages = new ArrayList<>();
        
        public SerializableSession() {}
    }
    
    /**
     * 可序列化的聊天消息类，用于持久化存储。
     */
    public static class SerializableChatMessage {
        public String role;
        public String content;
        
        public SerializableChatMessage() {}
    }
} 