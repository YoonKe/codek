package com.steins.codek.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义聊天气泡组件，支持圆角和尖角效果。
 * @author 0027013824
 */
public class ChatBubble extends JPanel {
    // 气泡类型
    public enum BubbleType {
        USER,       // 用户消息
        ASSISTANT,  // 助手消息
        SYSTEM      // 系统消息
    }

    // 现代化颜色方案
    private static final Color PRIMARY_BLUE = new Color(0x3B82F6);       // 蓝色主色调
    private static final Color LIGHT_PURPLE = new Color(0xA78BFA);       // 淡紫色强调色
    private static final Color BACKGROUND_WHITE = new Color(0xFFFFFF);   // 白色背景
    private static final Color LIGHT_GRAY = new Color(0xF3F4F6);         // 浅灰色背景
    private static final Color MEDIUM_GRAY = new Color(0xE5E7EB);        // 中灰色边框
    private static final Color DARK_GRAY = new Color(0x6B7280);          // 深灰色文本
    private static final Color TEXT_BLACK = new Color(0x1F2937);         // 近黑色文本

    // 气泡颜色
    private static final Color USER_BG = new Color(0xF3F4F6);        // 用户气泡背景 - 浅灰色
    private static final Color USER_BORDER = new Color(0xE5E7EB);    // 用户气泡边框 - 中灰色
    private static final Color USER_TEXT = new Color(0x1F2937);      // 用户文本 - 深灰色
    private static final Color ASSISTANT_BG = new Color(0xFFFFFF);   // 助手气泡背景 - 白色
    private static final Color ASSISTANT_BORDER = new Color(0xFFFFFF); // 助手气泡无边框
    private static final Color ASSISTANT_TEXT = new Color(0x1F2937); // 助手文本 - 近黑色
    private static final Color SYSTEM_BG = new Color(0xF0F9FF);      // 系统消息背景 - 淡蓝色
    private static final Color SYSTEM_BORDER = new Color(0xBAE6FD);  // 系统消息边框 - 亮蓝色
    private static final Color SYSTEM_TEXT = new Color(0x0369A1);    // 系统消息文本 - 蓝色

    private final BubbleType type;
    private final JEditorPane contentPane;
    private final int tipSize = JBUI.scale(8); // 气泡尖角大小
    private final Project project; // 添加项目引用，用于代码块组件创建
    private final List<CodeBlockComponent> codeBlocks = new ArrayList<>(); // 存储代码块组件列表

    // 代码块正则匹配模式: ```startLine:endLine:filePath\n代码内容\n```
    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(\\d+):(\\d+):([^\\n]+)\\n([\\s\\S]*?)\\n```");

    /**
     * 创建聊天气泡组件。
     * @param content 气泡内容HTML
     * @param type 气泡类型
     * @param project 当前项目
     */
    public ChatBubble(String content, BubbleType type, Project project) {
        this.type = type;
        this.project = project;
        setLayout(new BorderLayout());
        setOpaque(false);

        // 创建内容面板
        contentPane = new JEditorPane();
        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.setBorder(JBUI.Borders.empty(10)); // 增加内边距
        contentPane.setOpaque(false);
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        // 设置字体 - 使用系统默认字体，增大字号
        Font currentFont = UIUtil.getLabelFont();
        Font newFont = currentFont.deriveFont((float)(currentFont.getSize() + 1));
        contentPane.setFont(newFont);

        // 初始化时设置内容
        updateContent(content);

        // 根据类型设置现代化样式
        switch (type) {
            case USER:
                setBackground(USER_BG);
                contentPane.setForeground(USER_TEXT);
                // 用户消息使用灰色背景，有边框包裹
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(USER_BORDER, 1, true),
                        JBUI.Borders.empty()
                ));
                break;
            case ASSISTANT:
                setBackground(ASSISTANT_BG);
                contentPane.setForeground(ASSISTANT_TEXT);
                // AI消息无边框包裹
                setBorder(JBUI.Borders.empty());
                break;
            case SYSTEM:
                setBackground(SYSTEM_BG);
                contentPane.setForeground(SYSTEM_TEXT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SYSTEM_BORDER, 1, true),
                        JBUI.Borders.empty()
                ));
                break;
        }

        add(contentPane, BorderLayout.CENTER);
    }

    /**
     * 创建兼容旧版本的构造函数。
     * @param content 气泡内容HTML
     * @param type 气泡类型
     */
    public ChatBubble(String content, BubbleType type) {
        this(content, type, null);
    }

    /**
     * 更新气泡显示的内容。
     * @param htmlContent 新的HTML内容。
     */
    public void updateContent(String htmlContent) {
        // 清理之前的代码块组件
        clearCodeBlocks();

        // 处理并替换代码块
        String processedContent = htmlContent;
        if (project != null) {
            processedContent = processCodeBlocks(htmlContent);
        }

        contentPane.setText(processedContent);
        // 强制重新计算布局和大小
        revalidate();
        repaint();
    }

    /**
     * 处理内容中的代码块标记。
     * @param content 原始内容
     * @return 处理后的内容
     */
    private String processCodeBlocks(String content) {
        StringBuilder resultContent = new StringBuilder();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        int lastEnd = 0;
        boolean hasCodeBlocks = false;

        // 创建一个面板来容纳内联的代码块组件
        JPanel codeBlocksPanel = new JPanel();
        codeBlocksPanel.setLayout(new BoxLayout(codeBlocksPanel, BoxLayout.Y_AXIS));
        codeBlocksPanel.setOpaque(false);

        while (matcher.find()) {
            hasCodeBlocks = true;

            // 添加代码块前的文本
            resultContent.append(content.substring(lastEnd, matcher.start()));

            // 提取代码块信息
            int startLine = Integer.parseInt(matcher.group(1));
            int endLine = Integer.parseInt(matcher.group(2));
            String filePath = matcher.group(3);
            String code = matcher.group(4);

            // 创建代码块标记以在HTML中引用
            String codeBlockId = "code-block-" + codeBlocks.size();
            resultContent.append("<div id=\"" + codeBlockId + "\"></div>");

            // 创建代码块组件
            CodeBlockComponent codeBlock = new CodeBlockComponent(project, code, filePath, startLine, endLine);
            codeBlocks.add(codeBlock);

            // 添加到内容面板中
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setBorder(JBUI.Borders.empty(5, 0));
            wrapper.add(codeBlock, BorderLayout.CENTER);
            codeBlocksPanel.add(wrapper);

            lastEnd = matcher.end();
        }

        // 添加剩余文本
        resultContent.append(content.substring(lastEnd));

        // 如果有代码块，添加到内容下方
        if (hasCodeBlocks) {
            // 先设置HTML内容
            contentPane.setText(resultContent.toString());

            // 添加代码块面板到主面板
            remove(contentPane);  // 先移除内容面板

            JPanel combinedPanel = new JPanel();
            combinedPanel.setLayout(new BoxLayout(combinedPanel, BoxLayout.Y_AXIS));
            combinedPanel.setOpaque(false);

            combinedPanel.add(contentPane);
            combinedPanel.add(codeBlocksPanel);

            add(combinedPanel, BorderLayout.CENTER);
        } else {
            // 没有代码块，直接返回原内容
            return content;
        }

        return resultContent.toString();
    }

    /**
     * 清理代码块组件资源。
     */
    private void clearCodeBlocks() {
        for (CodeBlockComponent codeBlock : codeBlocks) {
            codeBlock.dispose();
        }
        codeBlocks.clear();
    }

    @Override
    public Dimension getPreferredSize() {
        // 让内容面板决定首选大小
        Dimension contentPrefSize = contentPane.getPreferredSize();
        Insets borderInsets = getInsets(); // 获取边框的边距

        // 计算包含边框的总宽度和高度
        int width = contentPrefSize.width + borderInsets.left + borderInsets.right;
        int height = contentPrefSize.height + borderInsets.top + borderInsets.bottom;

        // 如果有代码块，增加高度
        for (CodeBlockComponent codeBlock : codeBlocks) {
            height += codeBlock.getPreferredSize().height + JBUI.scale(10); // 代码块高度 + 间距
        }

        return new Dimension(width, height);
    }

    @Override
    public Dimension getMaximumSize() {
        // 根据父容器动态调整气泡的最大宽度
        if (getParent() != null) {
            int parentWidth = getParent().getWidth();
            // 使用弹性布局 - 占据更大比例的父容器宽度
            int maxWidth = Math.max((int) (parentWidth * 0.95), JBUI.scale(200));
            Dimension prefSize = getPreferredSize();
            // 高度不限制，宽度限制
            return new Dimension(Math.min(prefSize.width, maxWidth), prefSize.height);
        }
        return getPreferredSize(); // 如果没有父容器，则返回首选大小
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Insets insets = getInsets();
        int bubbleX = insets.left;
        int bubbleY = insets.top;
        int bubbleWidth = getWidth() - insets.left - insets.right;
        int bubbleHeight = getHeight() - insets.top - insets.bottom;

        // 创建圆角矩形
        int cornerRadius = JBUI.scale(12); // 更小的圆角半径，增强现代感
        RoundRectangle2D roundRect = new RoundRectangle2D.Float(
                bubbleX, bubbleY, bubbleWidth, bubbleHeight, cornerRadius, cornerRadius);

        // 填充背景
        g2.setColor(getBackground());
        g2.fill(roundRect);

        // 添加微妙的渐变效果（可选）
        if (type == BubbleType.USER) {
            // 用户气泡添加淡蓝色渐变
            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(0xDCEDFF),
                    0, getHeight(), USER_BG);
            g2.setPaint(gradient);
            g2.fill(roundRect);
        } else if (type == BubbleType.ASSISTANT) {
            // 助手气泡添加淡紫色渐变
            Color gradientStart = new Color(LIGHT_PURPLE.getRed(), LIGHT_PURPLE.getGreen(),
                                          LIGHT_PURPLE.getBlue(), 10); // 非常淡的紫色
            GradientPaint gradient = new GradientPaint(
                    0, 0, gradientStart,
                    0, getHeight(), ASSISTANT_BG);
            g2.setPaint(gradient);
            g2.fill(roundRect);
        }

        g2.dispose();
    }

    /**
     * 气泡边框，支持尖角效果。
     */
    private static class BubbleBorder extends AbstractBorder {
        public enum Position {
            LEFT, RIGHT
        }

        private final Color color;
        private final int tipSize;
        private final Position position;
        private final Insets borderInsets;

        public BubbleBorder(Color color, int tipSize, Position position) {
            this.color = color;
            this.tipSize = tipSize;
            this.position = position;
            // 预计算边距，避免每次调用 getBorderInsets 时都创建新对象
            int horizPadding = JBUI.scale(12); // 增加水平内边距
            int vertPadding = JBUI.scale(8);
            if (position == Position.LEFT) {
                this.borderInsets = new JBInsets(vertPadding, horizPadding + tipSize, vertPadding, horizPadding);
            } else { // RIGHT
                this.borderInsets = new JBInsets(vertPadding, horizPadding, vertPadding, horizPadding + tipSize);
            }
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            // 背景填充已在paintComponent中完成，边框绘制尖角
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);

            // 根据位置绘制尖角
            Polygon tip = new Polygon();
            int midY = y + height / 2; // 让尖角更居中
            int tipBaseY1 = midY - tipSize / 2;
            int tipBaseY2 = midY + tipSize / 2;

            if (position == Position.LEFT) {
                int tipX = x + tipSize;
                tip.addPoint(tipX, tipBaseY1);
                tip.addPoint(x, midY);
                tip.addPoint(tipX, tipBaseY2);
            } else { // RIGHT
                int tipX = x + width - tipSize - 1;
                tip.addPoint(tipX, tipBaseY1);
                tip.addPoint(x + width - 1, midY);
                tip.addPoint(tipX, tipBaseY2);
            }
            g2.fill(tip);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return borderInsets;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right);
            return insets;
        }

        @Override
        public boolean isBorderOpaque() {
            return true; // 边框是不透明的
        }
    }

    /**
     * 释放资源。
     */
    public void dispose() {
        clearCodeBlocks();
    }
}