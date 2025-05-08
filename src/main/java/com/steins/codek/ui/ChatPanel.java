package com.steins.codek.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.steins.codek.model.ChatMessage;
import com.steins.codek.model.ChatSession;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天面板组件，负责展示聊天气泡和管理聊天界面。
 * 支持流式输入显示、Markdown内容渲染等功能。
 * @author 0027013824
 */
public class ChatPanel extends JPanel {
    // 消息容器，使用BoxLayout垂直排列消息
    private final JPanel messagesContainer;
    // 滚动面板，包含消息容器
    private final JBScrollPane scrollPane;
    // 用于解析Markdown的解析器
    private final Parser mdParser;
    // 用于渲染HTML的渲染器
    private final HtmlRenderer htmlRenderer;
    // 当前消息列表
    private final List<ChatMessage> messages = new ArrayList<>();
    // 自动滚动控制
    private boolean autoScroll = true;
    // 正在流式输出的消息
    private ChatBubble streamingBubble;
    // 存储未完成的流式内容
    private final StringBuilder streamBuffer = new StringBuilder();
    // 记录最后一次用户触发的滚动位置
    private int lastUserScrollValue = 0;
    private final Project project;

    // 打字机效果相关
    private Timer typingTimer;
    private int typingSpeed = 15; // 毫秒/字符
    private String fullContent = "";
    private int currentPosition = 0;

    // 现代化颜色方案
    private static final Color BACKGROUND_WHITE = new Color(0xFFFFFF);   // 白色背景
    private static final Color LIGHT_GRAY = new Color(0xF3F4F6);         // 浅灰色背景

    /**
     * 构造函数。
     * @param project 当前项目
     */
    public ChatPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty());
        setBackground(BACKGROUND_WHITE);

        // 创建消息容器面板，使用BoxLayout垂直排列消息
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBorder(JBUI.Borders.empty(16)); // 增加内边距使布局更宽松
        messagesContainer.setBackground(BACKGROUND_WHITE); // 使用现代化白色背景

        // 创建滚动面板
        scrollPane = new JBScrollPane(messagesContainer);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setViewportBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // 平滑滚动

        // 添加滚动监听器，改进滚动行为
        scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            private int previousMaximum = 0;
            private boolean userScrolledUp = false;

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                JScrollBar scrollBar = (JScrollBar) e.getAdjustable();
                int value = e.getValue();
                int maximum = scrollBar.getMaximum();
                int extent = scrollBar.getModel().getExtent();

                // 检测用户是否手动滚动
                if (!e.getValueIsAdjusting() && previousMaximum > 0) {
                    if (value != maximum - extent) {
                        // 用户手动滚动到非底部位置
                        userScrolledUp = true;
                        lastUserScrollValue = value;
                        autoScroll = false;
                    } else if (userScrolledUp && value >= maximum - extent) {
                        // 用户手动滚动回底部
                        userScrolledUp = false;
                        autoScroll = true;
                    }
                }

                // 内容增加且自动滚动开启时，滚动到底部
                if (autoScroll && previousMaximum > 0 && maximum > previousMaximum) {
                    SwingUtilities.invokeLater(() -> {
                        if (scrollBar.isVisible()) {
                            scrollBar.setValue(maximum - extent);
                        }
                    });
                } else if (userScrolledUp && previousMaximum > 0 && maximum > previousMaximum) {
                    // 内容增加但用户已向上滚动，保持相对位置
                    int newValue = lastUserScrollValue + (maximum - previousMaximum);
                    if (newValue > maximum - extent) {
                        newValue = maximum - extent;
                    }
                    final int scrollTo = newValue;
                    SwingUtilities.invokeLater(() -> {
                        scrollBar.setValue(scrollTo);
                    });
                }

                previousMaximum = maximum;
            }
        });

        add(scrollPane, BorderLayout.CENTER);

        // 初始化Markdown解析器和HTML渲染器
        mdParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();
    }

    /**
     * 兼容旧版本的构造函数。
     */
    public ChatPanel() {
        this(null);
    }

    /**
     * 加载会话内容。
     * @param session 要加载的会话
     */
    public void loadSession(ChatSession session) {
        clear();
        if (session != null) {
            for (ChatMessage message : session.getMessages()) {
                if (!"system".equals(message.getRole())) { // 不显示系统消息
                    addMessage(message);
                }
            }
        }
    }

    /**
     * 添加一条消息。
     * @param message 要添加的消息
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        String role = message.getRole();
        String content = message.getContent();

        // 将Markdown内容转换为HTML (包含改进的样式)
        String html = markdownToHtml(content);

        // 创建气泡组件，传入项目引用
        ChatBubble bubble = new ChatBubble(html, "user".equals(role) ? ChatBubble.BubbleType.USER : ChatBubble.BubbleType.ASSISTANT, project);

        // 创建包装器，控制对齐和最大宽度
        JPanel wrapperPanel = createBubbleWrapper(bubble, "user".equals(role));

        messagesContainer.add(wrapperPanel);
        messagesContainer.add(Box.createVerticalStrut(JBUI.scale(5))); // 添加垂直间距

        messagesContainer.revalidate();
        messagesContainer.repaint();

        // 滚动到底部
        if (autoScroll) {
            SwingUtilities.invokeLater(this::scrollToBottom);
        }
    }

    /**
     * 添加流式输出内容，实现打字机效果。
     * @param chunk 文本片段
     */
    public void addStreamingContent(String chunk) {
        if (streamingBubble == null) {
            // 第一个文本块，创建一个新的助手气泡
            streamBuffer.setLength(0);
            fullContent = chunk;
            currentPosition = 0;

            // 创建初始气泡（空内容）
            streamingBubble = new ChatBubble("", ChatBubble.BubbleType.ASSISTANT, project);
            JPanel wrapperPanel = createBubbleWrapper(streamingBubble, false);

            messagesContainer.add(wrapperPanel);
            messagesContainer.add(Box.createVerticalStrut(JBUI.scale(8))); // 增加垂直间距

            // 启动打字机效果
            startTypingEffect();
        } else {
            // 追加到完整内容
            fullContent += chunk;

            // 如果打字机效果已经完成，重新启动
            if (typingTimer == null || !typingTimer.isRunning()) {
                startTypingEffect();
            }
        }

        // 滚动到底部
        if (autoScroll) {
            SwingUtilities.invokeLater(this::scrollToBottom);
        }

        messagesContainer.revalidate();
        messagesContainer.repaint();
    }

    /**
     * 启动打字机效果。
     */
    private void startTypingEffect() {
        if (typingTimer != null && typingTimer.isRunning()) {
            typingTimer.stop();
        }

        typingTimer = new Timer(typingSpeed, e -> {
            if (currentPosition < fullContent.length()) {
                currentPosition++;
                String partialContent = fullContent.substring(0, currentPosition);
                String html = markdownToHtml(partialContent);
                streamingBubble.updateContent(html);

                // 滚动到底部
                if (autoScroll) {
                    SwingUtilities.invokeLater(this::scrollToBottom);
                }
            } else {
                // 完成打字
                ((Timer)e.getSource()).stop();
            }
        });

        typingTimer.start();
    }

    /**
     * 创建气泡的包装面板，用于对齐和宽度控制。
     * @param bubble 气泡组件。
     * @param isUser 是否是用户消息。
     * @return 包装面板。
     */
    private JPanel createBubbleWrapper(ChatBubble bubble, boolean isUser) {
        // 最外层 wrapper，使用 BorderLayout
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setOpaque(false);
        wrapperPanel.setBorder(JBUI.Borders.empty(4, 16, 4, 16)); // 增加左右边距，使气泡更加分离

        // 内部对齐面板，使用 BoxLayout
        JPanel alignPanel = new JPanel();
        alignPanel.setLayout(new BoxLayout(alignPanel, BoxLayout.X_AXIS));
        alignPanel.setOpaque(false);

        // 获取容器宽度 - 动态计算以支持自适应
        int containerWidth = getWidth();
        if (containerWidth <= 0) {
            containerWidth = scrollPane.getViewport().getWidth();
            if (containerWidth <= 0) {
                containerWidth = 600; // 默认宽度
            }
        }

        // 设置气泡最大宽度为容器的85%，增加自适应性
        int maxWidth = (int)(containerWidth * 0.85);
        bubble.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));
        
        // 添加组件监听器以在面板大小变化时更新气泡宽度
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int newWidth = getWidth();
                if (newWidth > 0) {
                    int newMaxWidth = (int)(newWidth * 0.85);
                    bubble.setMaximumSize(new Dimension(newMaxWidth, Integer.MAX_VALUE));
                    wrapperPanel.revalidate();
                }
            }
        });

        // 添加头像标识（可选）
        if (isUser) {
            // 用户消息靠右对齐
            alignPanel.add(Box.createHorizontalGlue());
            alignPanel.add(bubble);
        } else {
            // AI消息靠左对齐
            alignPanel.add(bubble);
            alignPanel.add(Box.createHorizontalGlue());
        }

        wrapperPanel.add(alignPanel, BorderLayout.CENTER);
        return wrapperPanel;
    }

    /**
     * 完成流式输出。
     */
    public void completeStreaming() {
        if (streamingBubble != null) {
            // 停止打字机效果
            if (typingTimer != null && typingTimer.isRunning()) {
                typingTimer.stop();
            }

            // 确保显示完整内容
            String html = markdownToHtml(fullContent);
            streamingBubble.updateContent(html);

            // 添加到消息列表
            messages.add(new ChatMessage("assistant", fullContent));

            // 重置状态
            streamingBubble = null;
            streamBuffer.setLength(0);
            fullContent = "";
            currentPosition = 0;

            // 滚动到底部
            if (autoScroll) {
                SwingUtilities.invokeLater(this::scrollToBottom);
            }
        }
    }

    /**
     * 取消流式输出。
     */
    public void cancelStreaming() {
        if (streamingBubble != null) {
            // 找到包含streamingBubble的包装面板并移除
            // 这个逻辑需要调整，因为我们现在用了alignPanel
            Component[] components = messagesContainer.getComponents();
            for (int i = components.length - 1; i >= 0; i--) {
                if (components[i] instanceof JPanel) {
                    JPanel wrapper = (JPanel) components[i];
                    if (wrapper.getComponentCount() > 0 && wrapper.getComponent(0) instanceof JPanel) {
                        JPanel alignPanel = (JPanel) wrapper.getComponent(0);
                        if (alignPanel.getComponentCount() > 0 && alignPanel.getComponent(0) == streamingBubble) {
                            messagesContainer.remove(wrapper);
                            if (i > 0 && components[i-1] instanceof Box.Filler) { // 移除对应的间隔
                                messagesContainer.remove(i-1);
                            }
                            messagesContainer.revalidate();
                            messagesContainer.repaint();
                            break;
                        }
                    }
                }
            }

            // 清除流式状态
            streamingBubble = null;
            streamBuffer.setLength(0);
        }
    }

    /**
     * 清空聊天面板。
     */
    public void clear() {
        // 释放气泡资源
        Component[] components = messagesContainer.getComponents();
        for (Component component : components) {
            if (component instanceof JPanel) {
                Component[] children = ((JPanel) component).getComponents();
                for (Component child : children) {
                    if (child instanceof ChatBubble) {
                        ((ChatBubble) child).dispose();
                    }
                }
            }
        }

        messagesContainer.removeAll();
        messages.clear();
        streamBuffer.setLength(0);
        streamingBubble = null;
        messagesContainer.revalidate();
        messagesContainer.repaint();
    }

    /**
     * 将Markdown文本转换为HTML，并包含代码块样式。
     * @param markdown Markdown文本
     * @return 转换后的HTML
     */
    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        try {
            Node document = mdParser.parse(markdown);
            String html = htmlRenderer.render(document);

            // 注入现代化CSS样式，改善代码块可读性
            String codeBlockStyle = "background-color:#F1F5F9; color:#334155; padding:12px; " +
                    "border-radius:6px; overflow-x:auto; font-family:monospace; " +
                    "border:1px solid #E2E8F0; margin:8px 0; line-height:1.5;";
            String inlineCodeStyle = "background-color:#F1F5F9; color:#334155; padding:2px 4px; " +
                    "border-radius:3px; font-family:monospace; border:1px solid #E2E8F0;";

            // 替换代码块样式
            html = html.replace("<pre><code", "<pre style='" + codeBlockStyle + "'><code");
            html = html.replaceAll("(?<!<pre)><code>", "<code style='" + inlineCodeStyle + "'>");

            // 添加全局样式，使其更现代化
            String globalStyle = "<style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; line-height: 1.5; }" +
                    "p { margin: 0.5em 0; }" +
                    "a { color: #3B82F6; text-decoration: none; }" +
                    "a:hover { text-decoration: underline; }" +
                    "ul, ol { padding-left: 1.5em; }" +
                    "blockquote { border-left: 3px solid #E5E7EB; margin: 0.5em 0; padding-left: 1em; color: #6B7280; }" +
                    "</style>";

            html = "<html><head>" + globalStyle + "</head><body>" + html + "</body></html>";

            return html;
        }
        catch (Exception e) {
            // 发生错误时，返回原始文本作为段落
            return "<html><body><p>" + markdown.replace("<", "&lt;").replace(">", "&gt;") + "</p></body></html>";
        }
    }

    /**
     * 滚动到底部。
     */
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /**
     * 获取消息列表。
     * @return 消息列表
     */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * 设置自动滚动。
     * @param autoScroll 是否自动滚动
     */
    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }
}
