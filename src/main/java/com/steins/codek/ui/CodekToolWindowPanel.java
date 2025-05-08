package com.steins.codek.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.steins.codek.config.CodekConfig;
import com.steins.codek.model.ChatMessage;
import com.steins.codek.model.ChatSession;
import com.steins.codek.prompt.SystemPromptAssembler;
import com.steins.codek.service.CodeContextProvider;
import com.steins.codek.service.CodeSuggestionService;
import com.steins.codek.service.EditorService;
import com.steins.codek.service.LlmService;
import com.steins.codek.service.SessionManager;
import com.steins.codek.service.ToolExecutor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CodeK 工具窗口的 UI 面板。
 * 负责组织和管理工具窗口中的 Swing 组件。
 * @author 0027013824
 */
public class CodekToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(CodekToolWindowPanel.class);
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int HISTORY_LIMIT = 5; // 发送给LLM的历史消息数量
    
    private final JPanel mainPanel; // 整体面板
    private final Project project;
    private final ToolWindow toolWindow;

    // UI 组件
    private final SessionSelectorPanel sessionSelector; // 会话选择器，用于Popup
    private final ChatPanel chatPanel;              // 聊天面板 (主内容区域)
    private JBTextField inputField;                 // 用户输入框
    private JButton sendButton;                     // 发送按钮
    private JButton configButton;                   // 配置按钮 (顶部)
    private JButton contextButton;                  // 获取上下文按钮 (底部)
    private JButton applyCodeButton;                // 应用代码按钮 (底部)
    private JButton stopButton;                     // 停止按钮 (底部)
    private JButton historyButton;                  // 历史会话按钮 (顶部)
    private JComboBox<String> modelSelector;        // 模型选择器 (底部)
    private JLabel contextLabel;                    // 上下文显示标签 (底部)
    
    // 服务组件
    private final CodekConfig config;
    private final SessionManager sessionManager;
    private LlmService llmService;
    private final CodeContextProvider contextProvider;
    private final CodeSuggestionService suggestionService;
    private final ToolExecutor toolExecutor;
    private final EditorService editorService; // 添加 EditorService 引用
    
    // 状态
    private boolean isProcessing = false;
    private boolean hasInitialContext = false;
    private JBPopup historyPopup;

    /**
     * 构造函数。
     * @param project 当前项目。
     * @param toolWindow 当前工具窗口实例。
     */
    public CodekToolWindowPanel(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        
        // 获取服务实例
        this.config = ApplicationManager.getApplication().getService(CodekConfig.class);
        this.sessionManager = SessionManager.getInstance();
        this.toolExecutor = new ToolExecutor(project);
        this.contextProvider = new CodeContextProvider(project, this.toolExecutor);
        this.suggestionService = new CodeSuggestionService(project);
        this.editorService = new EditorService(project); // 初始化 EditorService
        
        // 初始化界面
        this.mainPanel = new JPanel(new BorderLayout(0, JBUI.scale(5))); // 垂直间距5
        this.mainPanel.setBorder(JBUI.Borders.empty());
        
        // 创建会话选择器 (用于Popup)
        sessionSelector = new SessionSelectorPanel();
        sessionSelector.setOnSessionSelected(session -> {
            handleSessionSelected(session);
            if (historyPopup != null && !historyPopup.isDisposed()) {
                historyPopup.cancel();
            }
        });
        
        // 1. 创建顶部导航栏
        JPanel topBarPanel = createTopBarPanel();
        mainPanel.add(topBarPanel, BorderLayout.NORTH);
        
        // 2. 创建主内容区域（聊天面板）- 传入项目参数
        chatPanel = new ChatPanel(project);
        mainPanel.add(chatPanel, BorderLayout.CENTER);
        
        // 3. 创建底部区域 (包含上下文/模型选择 和 输入/按钮)
        JPanel bottomAreaPanel = new JPanel(new BorderLayout(0, JBUI.scale(5)));
        bottomAreaPanel.setOpaque(false);
        
        // 3.1 上下文和模型选择区域
        JPanel contextModelPanel = createBottomContextModelPanel();
        bottomAreaPanel.add(contextModelPanel, BorderLayout.NORTH);
        
        // 3.2 输入框和操作按钮区域
        JPanel inputActionPanel = createBottomInputActionPanel();
        bottomAreaPanel.add(inputActionPanel, BorderLayout.SOUTH);
        
        mainPanel.add(bottomAreaPanel, BorderLayout.SOUTH);
        
        // 初始化事件监听器
        initializeListeners();
        
        // 初始化LLM服务
        initializeLlmService();
        
        // 检查配置
        checkAndNotifyConfiguration();
        
        // 初始化会话和上下文标签
        loadActiveSession();
        updateContextLabel(); // 初始加载上下文标签
    }
    
    /**
     * 创建顶部导航栏面板。
     * @return 顶部导航栏面板
     */
    private JPanel createTopBarPanel() {
        JPanel topBarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)); // 右对齐
        topBarPanel.setBorder(JBUI.Borders.empty(5, 10));
        
        // 历史会话按钮
        historyButton = new JButton(AllIcons.Actions.Back); // 或 AllIcons.Actions.ShowHistory
        historyButton.setToolTipText("查看历史会话");
        topBarPanel.add(historyButton);
        
        // 配置按钮
        configButton = new JButton(AllIcons.General.Settings);
        configButton.setToolTipText("配置API地址和密钥");
        topBarPanel.add(configButton);
        
        return topBarPanel;
    }
    
    /**
     * 创建底部区域 - 上下文和模型选择部分。
     * @return 面板
     */
    private JPanel createBottomContextModelPanel() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(10), 0));
        panel.setOpaque(true);
        panel.setBackground(new Color(0xF5F5F5)); // 浅灰色背景
        panel.setBorder(JBUI.Borders.empty(5, 10));
        
        // 上下文标签
        contextLabel = new JLabel("上下文: ", AllIcons.FileTypes.Any_type, SwingConstants.LEFT);
        contextLabel.setForeground(UIUtil.getLabelForeground());
        panel.add(contextLabel, BorderLayout.WEST);
        
        // 模型选择器
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        modelPanel.setOpaque(false);
        modelPanel.add(new JLabel("模型: "));
        modelSelector = new JComboBox<>(config.getSuggestedModels());
        modelSelector.setEditable(true);
        modelSelector.setSelectedItem(config.getCurrentModel());
        modelSelector.setToolTipText("选择或输入模型名称");
        modelPanel.add(modelSelector);
        panel.add(modelPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * 创建底部区域 - 输入框和操作按钮部分。
     * @return 面板
     */
    private JPanel createBottomInputActionPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        inputPanel.setBorder(JBUI.Borders.empty(0, 10, 10, 10)); // 底部留空多一点
        
        // 输入框
        inputField = new JBTextField();
        inputField.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1));
        inputField.putClientProperty("JTextField.Search.Gap", 0);
        inputField.putClientProperty("JTextField.Search.Icon", null);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isShiftDown()) {
                    int caretPosition = inputField.getCaretPosition();
                    String text = inputField.getText();
                    inputField.setText(text.substring(0, caretPosition) + "\n" + text.substring(caretPosition));
                    inputField.setCaretPosition(caretPosition + 1);
                    e.consume();
                }
            }
        });
        inputPanel.add(inputField, BorderLayout.CENTER);
        
        // 操作按钮面板 (右侧)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(5), 0));
        actionPanel.setOpaque(false);
        
        // 获取上下文按钮
        contextButton = new JButton(AllIcons.Actions.Lightning); 
        contextButton.setToolTipText("插入当前代码上下文");
        actionPanel.add(contextButton);
        
        // 应用代码按钮
        applyCodeButton = new JButton(AllIcons.Actions.MenuOpen);
        applyCodeButton.setToolTipText("将上次回复中的代码应用到编辑器");
        applyCodeButton.setEnabled(false);
        actionPanel.add(applyCodeButton);
        
        // 停止按钮
        stopButton = new JButton(AllIcons.Actions.Suspend);
        stopButton.setToolTipText("停止当前响应");
        stopButton.setEnabled(false);
        actionPanel.add(stopButton);
        
        // 发送按钮 (特殊处理，更突出)
        sendButton = new JButton("发送");
        // sendButton.setIcon(AllIcons.Actions.Execute);
        // 可以考虑设为默认按钮样式
        // getRootPane().setDefaultButton(sendButton); 
        actionPanel.add(sendButton);
        
        inputPanel.add(actionPanel, BorderLayout.EAST);
        
        return inputPanel;
    }

    /**
     * 初始化事件监听器。
     */
    private void initializeListeners() {
        // 顶部按钮
        historyButton.addActionListener(e -> showHistoryPopup());
        configButton.addActionListener(e -> showConfigDialog());
        
        // 底部按钮
        sendButton.addActionListener(e -> sendMessage());
        contextButton.addActionListener(e -> getAndDisplayContext());
        applyCodeButton.addActionListener(e -> applyCodeToEditor());
        stopButton.addActionListener(e -> stopProcessing());
        
        // 模型选择器
        modelSelector.addActionListener(e -> {
            if (!isProcessing) {
                String model = (String) modelSelector.getSelectedItem();
                if (model != null && !model.trim().isEmpty()) {
                    config.setCurrentModel(model.trim());
                    ChatSession activeSession = sessionManager.getActiveSession();
                    if (activeSession != null) {
                        activeSession.setModel(model.trim());
                        sessionManager.saveSession(activeSession);
                    }
                    initializeLlmService();
                }
            }
        });
    }
    
    /**
     * 更新上下文标签。
     */
    private void updateContextLabel() {
        ReadAction.run(() -> {
            String fileName = editorService.getCurrentFileName();
            String labelText = "上下文: " + (fileName != null ? fileName : "无活动文件");
            SwingUtilities.invokeLater(() -> contextLabel.setText(labelText));
        });
    }
    
    /**
     * 处理会话选择事件。
     * @param session 选择的会话
     */
    private void handleSessionSelected(ChatSession session) {
        if (isProcessing) {
            stopProcessing();
        }
        chatPanel.loadSession(session);
        modelSelector.setSelectedItem(session.getModel());
        sessionManager.setActiveSession(session.getId());
        updateApplyCodeButtonState();
        updateContextLabel(); // 切换会话时也更新上下文标签
    }

    /**
     * 加载活动会话。
     */
    private void loadActiveSession() {
        sessionSelector.refreshSessionList();
        ChatSession activeSession = sessionManager.getActiveSession();
        if (activeSession != null) {
            chatPanel.loadSession(activeSession);
            modelSelector.setSelectedItem(activeSession.getModel()); // 加载会话时设置模型选择器
            updateApplyCodeButtonState();
        } else {
            // 没有活动会话，可以创建一个新的或显示提示
            modelSelector.setSelectedItem(config.getCurrentModel()); // 确保模型选择器显示默认模型
        }
        updateContextLabel(); // 加载活动会话时更新上下文标签
    }
    
    /**
     * 获取并显示当前代码上下文。
     */
    private void getAndDisplayContext() {
        if (isProcessing) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String context = contextProvider.getContext();
                if (context != null && !context.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        ChatSession activeSession = sessionManager.getActiveSession();
                        if (activeSession == null) {
                            activeSession = sessionManager.createNewSession();
                            sessionSelector.refreshSessionList(); 
                        }
                        // 修改：代码块格式不再需要行号和路径
                        String message = "以下是当前代码上下文:\n\n```java\n" + context + "\n```"; 
                        chatPanel.addMessage(new ChatMessage("user", message));
                        activeSession.addMessage(new ChatMessage("user", message));
                        sessionManager.updateActiveSession();
            hasInitialContext = true;
                        updateContextLabel(); 
                    });
                }
            }
            catch (Exception e) {
                LOG.error("获取代码上下文时出错", e);
            }
        });
    }
    
    /**
     * 显示历史会话弹出窗口。
     */
    private void showHistoryPopup() {
        if (historyPopup != null && !historyPopup.isDisposed()) {
            historyPopup.cancel();
        }
        sessionSelector.refreshSessionList();
        historyPopup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(sessionSelector, sessionSelector)
                .setTitle("历史会话")
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setDimensionServiceKey(project, "CodekHistoryPopup", true)
                .createPopup();
        historyPopup.showUnderneathOf(historyButton);
    }
    
    /**
     * 初始化LLM服务。
     */
    private void initializeLlmService() {
        // 使用后台线程初始化，避免阻塞UI
        CompletableFuture.runAsync(() -> {
            String apiKey = config.getApiKey();
            String apiUrl = config.getApiUrl();
            String model = config.getCurrentModel();
            
            LlmService newService = null;
            if (apiKey != null && !apiKey.isEmpty() && apiUrl != null && !apiUrl.isEmpty()) {
                try {
                    // 将 project 传递给 LlmService 构造函数
                    newService = new LlmService(apiKey, model, apiUrl, project);
                    LOG.info("LlmService initialized successfully for model: " + model);
                } catch (Exception e) {
                    LOG.error("Failed to initialize LlmService", e);
                    // 可以在 UI 上显示错误提示
                }
            } else {
                 LOG.warn("LlmService not initialized: API key or URL is missing.");
            }
            
            // 在EDT线程中更新UI引用
            final LlmService finalService = newService;
            SwingUtilities.invokeLater(() -> {
                llmService = finalService;
                // 可以在这里更新UI状态，例如启用/禁用发送按钮
                checkAndNotifyConfiguration(); // 重新检查配置状态
            });
        });
    }
    
    /**
     * 发送消息。
     */
    private void sendMessage() {
        if (isProcessing || llmService == null) { // 增加 llmService null 检查
            if (llmService == null) {
                 LOG.warn("sendMessage called but LlmService is not initialized.");
                 checkAndNotifyConfiguration(); // 提示用户检查配置
            }
            return;
        }
        
        String input = inputField.getText().trim();
        if (input.isEmpty()) {
            return;
        }
        
            inputField.setText("");

        ChatSession activeSession = sessionManager.getActiveSession();
        boolean isNewSession = (activeSession == null);
        
        if (isNewSession) {
            activeSession = sessionManager.createNewSession();
            sessionSelector.refreshSessionList();
            // 将模型设置到新会话中
            activeSession.setModel(config.getCurrentModel()); 
        }
        
        // 添加用户消息到UI和会话
        ChatMessage userMessage = new ChatMessage("user", input);
        chatPanel.addMessage(userMessage);
        activeSession.addMessage(userMessage);
        sessionManager.updateActiveSession(); // 保存用户消息

        // 准备发送给 LLM 的消息列表
        List<ChatMessage> messagesToSend = new ArrayList<>();
        
        // *** 关键：添加系统提示词 ***
        String customInstructions = null;
        try {
            try {
                customInstructions = config.getCustomInstructions();
            }
            catch (NoSuchMethodError e) {
                LOG.warn("CodekConfig.getCustomInstructions() 方法未找到，将不使用自定义指令");
                customInstructions = null;
            }
        } catch (Exception e) {
            LOG.error("Error getting custom instructions from CodekConfig", e);
             // 保持 customInstructions 为 null
        }
        
        String systemPrompt = SystemPromptAssembler.assemblePrompt(
                toolExecutor.getAvailableTools(), 
                customInstructions // 使用获取到的或为 null 的指令
        );
        messagesToSend.add(new ChatMessage("system", systemPrompt));
        
        // 添加历史消息 (从会话中获取，排除旧的 system prompt)
        messagesToSend.addAll(prepareMessagesForLlm(activeSession)); // 使用修改后的 prepare 方法

        // *** 注意：当前的用户输入已经在 activeSession 中，
        // prepareMessagesForLlm 会包含它，所以不需要再手动添加 userMessage ***
        // messagesToSend.add(userMessage); // 不再需要这一行
        
            setProcessing(true);
        sendStreamRequestInternal(activeSession, messagesToSend);
    }
    
    /**
     * 内部方法，发送流式请求。
     * @param session 当前会话
     * @param messagesToSend 要发送的完整消息列表（已包含系统提示和历史）
     */
    private void sendStreamRequestInternal(final ChatSession session, final List<ChatMessage> messagesToSend) {
        
        llmService.streamChatCompletion(
                messagesToSend, 
                DEFAULT_TEMPERATURE,
                new LlmService.StreamingCallback() {
                    private final StringBuilder fullResponse = new StringBuilder();
                    private ChatMessage lastAssistantMessage = null; // 用于存储完整的助手响应

                    @Override
                    public void onChunkReceived(String textChunk) {
                        fullResponse.append(textChunk);
                        SwingUtilities.invokeLater(() -> chatPanel.addStreamingContent(textChunk));
                    }

                    @Override
                    public void onComplete() {
                        final String completeResponse = fullResponse.toString();
                        // 创建包含完整内容的助手消息
                        lastAssistantMessage = new ChatMessage("assistant", completeResponse);
                        
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.completeStreaming();
                            // 在会话中添加完整的助手消息（如果它非空）
                            if (!completeResponse.trim().isEmpty() || 
                                (lastAssistantMessage.getToolCalls() != null && !lastAssistantMessage.getToolCalls().isEmpty())) {
                                session.addMessage(lastAssistantMessage);
                                sessionManager.updateActiveSession();
                            }
                            updateApplyCodeButtonState();
                        setProcessing(false);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        final String errorMessage = "发生错误: " + e.getMessage();
                        // 也创建一个错误消息对象
                        lastAssistantMessage = new ChatMessage("assistant", errorMessage);

                        SwingUtilities.invokeLater(() -> {
                            chatPanel.cancelStreaming();
                            chatPanel.addMessage(lastAssistantMessage); // 显示错误消息
                            session.addMessage(lastAssistantMessage); // 将错误消息也添加到会话历史
                            sessionManager.updateActiveSession();
                        setProcessing(false);
                        });
                    }
                    
                    // 可选：处理 LlmService 可能回调的其他方法，例如 onToolCallStart 等
                    // @Override
                    // public void onAssistantMessageUpdate(ChatMessage message) {
                    //     // 如果 LlmService 在 onComplete 前就能提供包含 ToolCalls 的完整消息对象
                    //     lastAssistantMessage = message; 
                    // }
                });
    }
    
    /**
     * 准备发送给LLM的消息列表，包含指定数量的历史记录，并排除系统消息。
     * @param session 当前会话
     * @return 包含历史记录的消息列表 (不含 system 角色)
     */
    private List<ChatMessage> prepareMessagesForLlm(ChatSession session) {
        List<ChatMessage> history = session.getMessages().stream()
                .filter(m -> !"system".equals(m.getRole())) // 过滤掉 system 消息
                .collect(Collectors.toList());
        
        if (history.size() <= HISTORY_LIMIT) {
            return history;
        }
        
        int startIndex = history.size() - HISTORY_LIMIT;
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }
    
    /**
     * 应用代码到编辑器。
     */
    private void applyCodeToEditor() {
        List<ChatMessage> messages = chatPanel.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        String content = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).getRole())) {
                content = messages.get(i).getContent();
                break;
            }
        }
        if (content == null) {
            return;
        }
        final String finalContent = content;
        CompletableFuture.runAsync(() -> {
            try {
                // 修改：使用新的格式提取代码
                // Pattern pattern = Pattern.compile("```\\w*\\n([\\s\\S]*?)\\n```");
                Pattern pattern = Pattern.compile("```(?:\\d+:\\d+:[^\\n]+|\\w*)\\n([\\s\\S]*?)\n```");
                Matcher matcher = pattern.matcher(finalContent);
                String codeToApply = null;
                // 查找最后一个匹配的代码块
                while (matcher.find()) {
                     codeToApply = matcher.group(1);
                }
                
                if (codeToApply != null && !codeToApply.isEmpty()) {
                    // 移除 applyCodeToEditor 对 startLine/endLine/filePath 的依赖
                    // 这些信息现在应该由 CodeBlockComponent 处理
                    // boolean applied = suggestionService.applyCodeToEditor(codeToApply, startLine, endLine, filePath);
                    // 这里仅作示例，实际应用应由 CodeBlockComponent 触发
                    LOG.info("Code extracted for potential application (should be handled by CodeBlockComponent click):");
                    LOG.info(codeToApply);
                    // 实际上，applyCodeButton 的逻辑可能需要改变，
                    // 或者根本不需要这个按钮了，因为应用操作在 CodeBlockComponent 内部。
                    // 如果保留按钮，它可能需要查找最后一个包含可应用代码块的消息，
                    // 然后模拟点击对应的 CodeBlockComponent 按钮，但这比较复杂。
                    // 暂时注释掉实际应用逻辑。
                    /*
                    boolean applied = suggestionService.applyCodeToEditor(codeToApply);
                    if (!applied) {
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.addMessage(new ChatMessage("assistant", "无法应用代码。请确保有打开的编辑器窗口。"));
                            ChatSession activeSession = sessionManager.getActiveSession();
                            if (activeSession != null) {
                                activeSession.addMessage(new ChatMessage("assistant", "无法应用代码。请确保有打开的编辑器窗口。"));
                                sessionManager.updateActiveSession();
                            }
                        });
                    }
                    */
                    SwingUtilities.invokeLater(() -> {
                          chatPanel.addMessage(new ChatMessage("system", "(代码块已识别，请点击代码块上方的 '应用' 按钮应用修改)"));
                       });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.addMessage(new ChatMessage("assistant", "在最后的消息中未找到可应用的代码块。"));
                        // ... (更新会话) ...
                    });
                }
            } catch (Exception e) {
                LOG.error("提取或应用代码时出错", e);
                // ... (错误处理) ...
            }
        });
    }

    /**
     * 显示配置对话框。
     */
    private void showConfigDialog() {
        CompletableFuture.supplyAsync(() -> {
            String apiUrl = config.getApiUrl();
            String apiKey = config.getApiKey();
            return new String[] { apiUrl, apiKey };
        }).thenAccept(configValues -> {
        SwingUtilities.invokeLater(() -> {
                ConfigDialog dialog = new ConfigDialog(project, configValues[0], configValues[1]);
                if (dialog.showAndGet()) {
                    CompletableFuture.runAsync(() -> {
                        config.setApiUrl(dialog.getApiUrl());
                        config.setApiKey(dialog.getApiKey());
                        initializeLlmService();
                    });
                }
            });
        });
    }

    /**
     * 检查配置，如果没有配置则提示用户。
     */
    private void checkAndNotifyConfiguration() {
        boolean configured = !(config.getApiKey() == null || config.getApiKey().isEmpty() ||
                             config.getApiUrl() == null || config.getApiUrl().isEmpty());
        
        SwingUtilities.invokeLater(() -> {
            // 移除旧的通知（如果存在）
            Component[] components = chatPanel.getComponents();
            for(Component comp : components) {
                if (comp instanceof JPanel && "configNotification".equals(comp.getName())) {
                    chatPanel.remove(comp);
                }
            }
            
            if (!configured && llmService == null) { // 仅在未配置且服务未初始化时显示
                JPanel notificationPanel = new JPanel(new BorderLayout());
                notificationPanel.setName("configNotification"); // 添加名称以便查找和移除
                notificationPanel.setBorder(JBUI.Borders.empty(10));
                notificationPanel.setBackground(new Color(0xFFF8DC));
                JLabel label = new JBLabel("请先点击右上角的设置按钮配置API地址和密钥。");
                label.setIcon(AllIcons.General.Warning); 
                notificationPanel.add(label, BorderLayout.CENTER);
                
                chatPanel.add(notificationPanel, BorderLayout.NORTH);
            }
            chatPanel.revalidate();
            chatPanel.repaint();
            // 根据配置状态启用/禁用发送按钮
            sendButton.setEnabled(configured && !isProcessing);
        });
    }

    /**
     * 设置处理状态。
     * @param processing 是否正在处理
     */
    private void setProcessing(boolean processing) {
        this.isProcessing = processing;
        
        // 更新UI状态
        sendButton.setEnabled(!processing);
        inputField.setEnabled(!processing);
        contextButton.setEnabled(!processing);
        modelSelector.setEnabled(!processing);
        stopButton.setEnabled(processing);
    }
    
    /**
     * 停止处理。
     */
    private void stopProcessing() {
        // TODO: 实现取消 LlmService 中的 OkHttp Call
        LOG.warn("stopProcessing - 需要实现取消 OkHttp Call 的逻辑");
        chatPanel.cancelStreaming();
        setProcessing(false);
    }
    
    /**
     * 更新应用代码按钮状态。
     */
    private void updateApplyCodeButtonState() {
        // 由于应用逻辑移至 CodeBlockComponent，此按钮可能不再需要精确判断代码块内容
        // 可以简单地基于最后一条消息是否来自助手来启用
        boolean enabled = false;
        if (sessionManager.getActiveSession() != null) {
            List<ChatMessage> messages = chatPanel.getMessages();
            if (!messages.isEmpty() && "assistant".equals(messages.get(messages.size() - 1).getRole())) {
                // 简单的启用逻辑，或者完全移除此按钮
                 enabled = true; 
                 // 或者更复杂的检查，查找最后一个消息中是否有 CodeBlockComponent？但这耦合度高
            }
        }
        final boolean finalEnabled = enabled;
        SwingUtilities.invokeLater(() -> applyCodeButton.setEnabled(finalEnabled));
    }
    
    /**
     * 获取面板内容。
     * @return 面板内容
     */
    public JPanel getContent() {
        return mainPanel;
    }
    
    /**
     * 配置对话框。
     */
    private static class ConfigDialog extends DialogWrapper {
        private final JTextField apiUrlField;
        private final JPasswordField apiKeyField;
        private final String initialApiUrl;
        private final String initialApiKey;

        protected ConfigDialog(@Nullable Project project, String apiUrl, String apiKey) {
            super(project);
            this.initialApiUrl = apiUrl;
            this.initialApiKey = apiKey;
            this.apiUrlField = new JTextField(apiUrl, 30);
            this.apiKeyField = new JPasswordField(apiKey, 30);
            
            setTitle("CodeK 配置");
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(JBUI.Borders.empty(10));
            
            JPanel formPanel = new JPanel(new GridLayout(2, 2, 5, 5));
            formPanel.add(new JBLabel("API 地址:"));
            formPanel.add(apiUrlField);
            formPanel.add(new JBLabel("API 密钥:"));
            formPanel.add(apiKeyField);
            
            panel.add(formPanel, BorderLayout.CENTER);
            return panel;
        }

        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            if (apiUrlField.getText().trim().isEmpty()) {
                return new ValidationInfo("API地址不能为空", apiUrlField);
            }
            
            if (new String(apiKeyField.getPassword()).trim().isEmpty()) {
                return new ValidationInfo("API密钥不能为空", apiKeyField);
            }
            
            return null;
        }
        
        public String getApiUrl() {
            return apiUrlField.getText().trim();
        }
        
        public String getApiKey() {
            return new String(apiKeyField.getPassword()).trim();
        }
    }
} 