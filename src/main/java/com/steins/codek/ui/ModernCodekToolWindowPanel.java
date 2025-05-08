package com.steins.codek.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.steins.codek.config.CodekConfig;
import com.steins.codek.model.ChatMessage;
import com.steins.codek.model.ChatSession;
import com.steins.codek.prompt.SystemPromptAssembler;
import com.steins.codek.service.CodeContextProvider;
import com.steins.codek.service.EditorService;
import com.steins.codek.service.LlmService;
import com.steins.codek.service.SessionManager;
import com.steins.codek.service.ToolExecutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CodeK 工具窗口的现代化 UI 面板。
 * 负责组织和管理工具窗口中的 Swing 组件。
 * 采用现代化设计，使用白色、灰色、蓝色、淡紫色组合。
 * @author 0027013824
 */
public class ModernCodekToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(ModernCodekToolWindowPanel.class);

    // 现代化颜色方案
    private static final Color PRIMARY_BLUE = new Color(0x3B82F6);       // 蓝色主色调
    private static final Color LIGHT_PURPLE = new Color(0xA78BFA);       // 淡紫色强调色
    private static final Color BACKGROUND_WHITE = new Color(0xFFFFFF);   // 白色背景
    private static final Color LIGHT_GRAY = new Color(0xF3F4F6);         // 浅灰色背景
    private static final Color MEDIUM_GRAY = new Color(0xE5E7EB);        // 中灰色边框
    private static final Color DARK_GRAY = new Color(0x6B7280);          // 深灰色文本
    private static final Color TEXT_BLACK = new Color(0x1F2937);         // 近黑色文本

    private final JPanel mainPanel; // 整体面板
    private final Project project;
    private final ToolWindow toolWindow;
    private final CodekConfig config;
    private final SessionManager sessionManager;

    // 服务组件
    private LlmService llmService;
    private final CodeContextProvider contextProvider;
    private final ToolExecutor toolExecutor;
    private final EditorService editorService;
    private final SessionSelectorPanel sessionSelector;

    // UI 组件
    private ChatPanel chatPanel;              // 聊天面板 (主内容区域)
    private JBTextField inputField;           // 用户输入框
    private JButton sendButton;               // 发送按钮
    private JButton configButton;             // 配置按钮
    private JButton historyButton;            // 历史会话按钮
    private JComboBox<String> modelSelector;  // 模型选择器
    private JButton fileButton;               // 文件选择按钮

    // 状态控制
    private boolean isProcessing = false;
    private static final double DEFAULT_TEMPERATURE = 0.7;

    /**
     * 构造函数。
     * @param project 当前项目。
     * @param toolWindow 当前工具窗口实例。
     */
    public ModernCodekToolWindowPanel(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.config = ApplicationManager.getApplication().getService(CodekConfig.class);
        this.sessionManager = SessionManager.getInstance();

        // 初始化服务组件
        this.toolExecutor = new ToolExecutor(project);
        this.contextProvider = new CodeContextProvider(project, this.toolExecutor);
        this.editorService = new EditorService(project);
        this.sessionSelector = new SessionSelectorPanel();
        this.sessionSelector.setOnSessionSelected(this::switchSession);

        // 初始化 LLM 服务
        initLlmService();

        // 初始化主面板
        this.mainPanel = new JPanel(new BorderLayout());

        // 创建现代化布局
        createModernLayout();
    }

    /**
     * 初始化 LLM 服务。
     */
    private void initLlmService() {
        try {
            String apiKey = config.getApiKey();
            String apiUrl = config.getApiUrl();
            String model = config.getCurrentModel();

            if (apiKey != null && !apiKey.isEmpty() && apiUrl != null && !apiUrl.isEmpty()) {
                // 注意：LlmService构造函数需要四个参数，包括项目实例
                llmService = new LlmService(apiKey, model, apiUrl, project);
                // LlmService已经在构造函数中自动获取了可用工具，不需要再注册
            } else {
                LOG.warn("API 配置不完整，无法初始化 LLM 服务");
            }
        } catch (Exception e) {
            LOG.error("初始化 LLM 服务时出错", e);
        }
    }

    /**
     * 创建现代化布局。
     * 实现类似Augment的UI设计，使用白色、灰色、蓝色、淡紫色组合。
     */
    private void createModernLayout() {
        // 设置主面板背景色和边框
        mainPanel.setBackground(BACKGROUND_WHITE);
        mainPanel.setBorder(JBUI.Borders.empty());

        // 1. 创建顶部工具栏
        JPanel topBar = createTopBar();
        mainPanel.add(topBar, BorderLayout.NORTH);

        // 2. 创建中间聊天区域
        chatPanel = new ChatPanel(project);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        // 3. 创建底部输入区域
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 初始化事件监听器
        initializeListeners();
    }

    /**
     * 创建顶部工具栏。
     * @return 顶部工具栏面板
     */
    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(JBUI.Borders.empty(8, 12));
        topBar.setBackground(BACKGROUND_WHITE);

        // 左侧标题
        JLabel titleLabel = new JLabel("CodeK Assistant");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(TEXT_BLACK);
        topBar.add(titleLabel, BorderLayout.WEST);

        // 右侧按钮组
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.setOpaque(false);

        // 历史会话按钮
        historyButton = createIconButton(AllIcons.Vcs.History, "历史会话");
        historyButton.setForeground(DARK_GRAY);

        // 配置按钮
        configButton = createIconButton(AllIcons.General.Settings, "设置");
        configButton.setForeground(DARK_GRAY);

        rightButtons.add(historyButton);
        rightButtons.add(configButton);
        topBar.add(rightButtons, BorderLayout.EAST);

        return topBar;
    }

    /**
     * 创建底部输入区域。
     * @return 底部输入区域面板
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.setBorder(JBUI.Borders.empty());
        bottomPanel.setBackground(BACKGROUND_WHITE);

        // 文件预览区 (1/10高度)
        JPanel filePreviewPanel = new JPanel(new BorderLayout());
        filePreviewPanel.setBackground(LIGHT_GRAY);
        filePreviewPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, MEDIUM_GRAY));
        filePreviewPanel.setPreferredSize(new Dimension(-1, JBUI.scale(30)));
        
        JLabel filePreviewLabel = new JLabel("文件预览区");
        filePreviewLabel.setForeground(DARK_GRAY);
        filePreviewLabel.setBorder(JBUI.Borders.empty(5, 10));
        filePreviewPanel.add(filePreviewLabel, BorderLayout.WEST);
        
        bottomPanel.add(filePreviewPanel, BorderLayout.NORTH);

        // 输入区域 (中间部分)
        JPanel inputAreaPanel = new JPanel(new BorderLayout());
        inputAreaPanel.setBackground(BACKGROUND_WHITE);
        inputAreaPanel.setBorder(JBUI.Borders.empty(10));
        
        // 输入文本区域 (使用JTextArea替代JBTextField以支持多行)
        JTextArea inputArea = new JTextArea("输入内容...");
        inputArea.setFont(inputArea.getFont().deriveFont(13f));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBackground(BACKGROUND_WHITE);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MEDIUM_GRAY, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        
        // 替换原来的inputField
        this.inputField = new JBTextField();
        inputField.setVisible(false); // 隐藏但保留引用以避免空指针
        
        // 将输入事件从JTextArea转发到inputField
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    if (!isProcessing) {
                        inputField.setText(inputArea.getText());
                        inputArea.setText("");
                        sendMessage();
                    }
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                // 实时同步内容到inputField
                inputField.setText(inputArea.getText());
            }
        });
        
        // 焦点获得时清除提示文字
        inputArea.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (inputArea.getText().equals("输入内容...")) {
                    inputArea.setText("");
                }
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (inputArea.getText().isEmpty()) {
                    inputArea.setText("输入内容...");
                }
            }
        });
        
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setBorder(JBUI.Borders.empty());
        inputScrollPane.setPreferredSize(new Dimension(-1, JBUI.scale(100)));
        
        inputAreaPanel.add(inputScrollPane, BorderLayout.CENTER);
        bottomPanel.add(inputAreaPanel, BorderLayout.CENTER);

        // 底部操作栏 (1/5高度)
        JPanel actionBarPanel = new JPanel(new BorderLayout());
        actionBarPanel.setBackground(LIGHT_GRAY);
        actionBarPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, MEDIUM_GRAY));
        actionBarPanel.setPreferredSize(new Dimension(-1, JBUI.scale(40)));
        
        // 左侧区域 - 模型选择和文件按钮
        JPanel leftActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftActionsPanel.setOpaque(false);
        
        // 模型选择下拉框
        modelSelector = new JComboBox<>(CodekConfig.SUGGESTED_MODELS);
        modelSelector.setFont(modelSelector.getFont().deriveFont(12f));
        modelSelector.setForeground(DARK_GRAY);
        modelSelector.setBackground(BACKGROUND_WHITE);
        modelSelector.setBorder(BorderFactory.createLineBorder(MEDIUM_GRAY, 1));
        
        // 文件选择按钮 - 使用@图标
        fileButton = new JButton("@");
        fileButton.setFont(fileButton.getFont().deriveFont(Font.BOLD, 14f));
        fileButton.setForeground(PRIMARY_BLUE);
        fileButton.setBackground(BACKGROUND_WHITE);
        fileButton.setBorder(BorderFactory.createLineBorder(MEDIUM_GRAY, 1));
        fileButton.setPreferredSize(new Dimension(JBUI.scale(30), JBUI.scale(30)));
        
        leftActionsPanel.add(modelSelector);
        leftActionsPanel.add(fileButton);
        
        // 右侧区域 - 发送按钮
        JPanel rightActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        rightActionsPanel.setOpaque(false);
        
        // 发送按钮
        sendButton = new JButton("➤");
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD, 16f));
        sendButton.setForeground(Color.WHITE);
        sendButton.setBackground(PRIMARY_BLUE);
        sendButton.setBorder(BorderFactory.createLineBorder(PRIMARY_BLUE.darker(), 1));
        sendButton.setPreferredSize(new Dimension(JBUI.scale(40), JBUI.scale(30)));
        
        rightActionsPanel.add(sendButton);
        
        actionBarPanel.add(leftActionsPanel, BorderLayout.WEST);
        actionBarPanel.add(rightActionsPanel, BorderLayout.EAST);
        
        bottomPanel.add(actionBarPanel, BorderLayout.SOUTH);
        
        return bottomPanel;
    }

    /**
     * 创建图标按钮。
     * @param icon 按钮图标
     * @param tooltip 提示文本
     * @return 图标按钮
     */
    private JButton createIconButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(JBUI.Borders.empty(4));
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * 初始化事件监听器。
     */
    private void initializeListeners() {
        // 发送按钮点击事件
        sendButton.addActionListener(e -> {
            if (isProcessing) {
                stopProcessing();
            } else {
                sendMessage();
            }
        });

        // 输入框回车事件
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume(); // 阻止默认的回车换行
                    if (!isProcessing) {
                        sendMessage();
                    }
                }
            }
        });

        // 配置按钮点击事件
        configButton.addActionListener(e -> showConfigDialog());

        // 历史按钮点击事件
        historyButton.addActionListener(e -> showHistoryDialog());

        // 文件按钮点击事件
        fileButton.addActionListener(e -> selectFile());

        // 模型选择器事件
        modelSelector.addActionListener(e -> {
            String selectedModel = (String) modelSelector.getSelectedItem();
            if (selectedModel != null && !selectedModel.isEmpty()) {
                config.setCurrentModel(selectedModel);
                // 更新当前会话的模型
                ChatSession activeSession = sessionManager.getActiveSession();
                if (activeSession != null) {
                    activeSession.setModel(selectedModel);
                    sessionManager.updateActiveSession();
                }
                // 重新初始化 LLM 服务
                initLlmService();
            }
        });
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
     * 切换会话。
     * @param session 要切换到的会话
     */
    private void switchSession(ChatSession session) {
        if (session == null) {
            return;
        }

        // 使用ChatPanel的loadSession方法加载会话
        chatPanel.loadSession(session);

        // 更新模型选择器
        String sessionModel = session.getModel();
        if (sessionModel != null && !sessionModel.isEmpty()) {
            for (int i = 0; i < modelSelector.getItemCount(); i++) {
                if (modelSelector.getItemAt(i).equals(sessionModel)) {
                    modelSelector.setSelectedIndex(i);
                    break;
                }
            }
        }

        // 确保自动滚动开启
        chatPanel.setAutoScroll(true);
    }

    /**
     * 准备发送给 LLM 的消息列表。
     * @param session 当前会话
     * @return 要发送的消息列表
     */
    private List<ChatMessage> prepareMessagesForLlm(ChatSession session) {
        List<ChatMessage> allMessages = session.getMessages();
        List<ChatMessage> messagesToSend = new ArrayList<>();

        // 只取最近的几条消息，并跳过系统消息
        int count = 0;
        int limit = 5; // 限制发送的历史消息数量

        for (int i = allMessages.size() - 1; i >= 0 && count < limit; i--) {
            ChatMessage message = allMessages.get(i);
            if (!"system".equals(message.getRole())) {
                messagesToSend.add(0, message);
                count++;
            }
        }

        return messagesToSend;
    }

    /**
     * 发送消息。
     */
    private void sendMessage() {
        if (isProcessing || llmService == null) {
            if (llmService == null) {
                LOG.warn("sendMessage called but LlmService is not initialized.");
                // 提示用户检查配置
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            mainPanel,
                            "API 配置不完整，请先配置 API 密钥和地址。",
                            "API 配置错误",
                            JOptionPane.WARNING_MESSAGE
                    );
                });
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

        // 添加系统提示词
        String customInstructions = null;
        // 注意：CodekConfig类没有getCustomInstructions方法
        // 自定义指令可以在未来版本中实现

        String systemPrompt = SystemPromptAssembler.assemblePrompt(
                toolExecutor.getAvailableTools(),
                customInstructions
        );
        messagesToSend.add(new ChatMessage("system", systemPrompt));

        // 添加历史消息
        messagesToSend.addAll(prepareMessagesForLlm(activeSession));

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
                            if (!completeResponse.trim().isEmpty()) {
                                session.addMessage(lastAssistantMessage);
                                sessionManager.updateActiveSession();
                            }
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
                });
    }

    /**
     * 设置处理状态。
     * @param processing 是否正在处理
     */
    private void setProcessing(boolean processing) {
        isProcessing = processing;
        SwingUtilities.invokeLater(() -> {
            // 更新发送按钮的文本和外观
            if (processing) {
                sendButton.setText("■");
                sendButton.setBackground(new Color(0xEF4444)); // 红色
            } else {
                sendButton.setText("➤");
                sendButton.setBackground(PRIMARY_BLUE); // 蓝色
            }

            // 获取输入区域组件
            Component[] components = ((JPanel)mainPanel.getComponent(2)).getComponents();
            JTextArea inputArea = null;
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    Component[] innerComps = ((JPanel)comp).getComponents();
                    for (Component inner : innerComps) {
                        if (inner instanceof JScrollPane) {
                            Component view = ((JScrollPane)inner).getViewport().getView();
                            if (view instanceof JTextArea) {
                                inputArea = (JTextArea)view;
                                break;
                            }
                        }
                    }
                }
                if (inputArea != null) break;
            }
            
            // 禁用/启用输入区域
            if (inputArea != null) {
                inputArea.setEnabled(!processing);
            }
            
            // 发送按钮始终保持启用，但功能会切换
            inputField.setEnabled(!processing);
            modelSelector.setEnabled(!processing);
            fileButton.setEnabled(!processing);
        });
    }

    /**
     * 显示配置对话框。
     */
    private void showConfigDialog() {
        // 创建配置对话框
        JDialog configDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(mainPanel), "API 配置", true);
        configDialog.setLayout(new BorderLayout());
        configDialog.setSize(400, 300);
        configDialog.setLocationRelativeTo(mainPanel);

        // 创建配置面板
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        // API 密钥
        configPanel.add(new JLabel("API 密钥:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField apiKeyField = new JTextField(config.getApiKey(), 20);
        configPanel.add(apiKeyField, gbc);

        // API 地址
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        configPanel.add(new JLabel("API 地址:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField apiUrlField = new JTextField(config.getApiUrl(), 20);
        configPanel.add(apiUrlField, gbc);

        // 模型
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        configPanel.add(new JLabel("默认模型:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JComboBox<String> modelField = new JComboBox<>(CodekConfig.SUGGESTED_MODELS);
        modelField.setSelectedItem(config.getCurrentModel());
        configPanel.add(modelField, gbc);

        // 自定义指令
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        configPanel.add(new JLabel("自定义指令 (当前版本不支持):"), gbc);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JTextArea instructionsArea = new JTextArea("", 5, 20);
        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        instructionsArea.setEnabled(false); // 禁用文本区域
        instructionsArea.setText("自定义指令功能将在未来版本中实现");
        JScrollPane instructionsScroll = new JScrollPane(instructionsArea);
        configPanel.add(instructionsScroll, gbc);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("保存");
        JButton cancelButton = new JButton("取消");

        saveButton.addActionListener(e -> {
            // 保存配置
            config.setApiKey(apiKeyField.getText().trim());
            config.setApiUrl(apiUrlField.getText().trim());
            config.setCurrentModel((String) modelField.getSelectedItem());
            // 注意：CodekConfig类没有setCustomInstructions方法
            // 自定义指令可以在未来版本中实现
            // config.setCustomInstructions(instructionsArea.getText().trim());

            // 重新初始化 LLM 服务
            initLlmService();

            configDialog.dispose();
        });

        cancelButton.addActionListener(e -> configDialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        configDialog.add(configPanel, BorderLayout.CENTER);
        configDialog.add(buttonPanel, BorderLayout.SOUTH);

        configDialog.setVisible(true);
    }

    /**
     * 显示历史对话框。
     */
    private void showHistoryDialog() {
        // 显示会话选择器弹出窗口
        JBPopupFactory.getInstance()
                .createComponentPopupBuilder(sessionSelector, null)
                .setTitle("历史会话")
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setMinSize(new Dimension(300, 400))
                .createPopup()
                .showUnderneathOf(historyButton);
    }

    /**
     * 选择文件。
     */
    private void selectFile() {
        // 获取当前上下文
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
                        // 代码块格式
                        String message = "以下是当前代码上下文:\n\n```java\n" + context + "\n```";
                        chatPanel.addMessage(new ChatMessage("user", message));
                        activeSession.addMessage(new ChatMessage("user", message));
                        sessionManager.updateActiveSession();
                    });
                }
            }
            catch (Exception e) {
                LOG.error("获取代码上下文时出错", e);
            }
        });
    }

    /**
     * 获取主面板。
     * @return 主面板
     */
    public JPanel getContent() {
        return mainPanel;
    }
}
