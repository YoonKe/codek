package com.steins.codek.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.steins.codek.model.ChatSession;
import com.steins.codek.service.SessionManager;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 会话选择器面板，用于显示和选择聊天会话。
 * @author 0027013824
 */
public class SessionSelectorPanel extends JPanel {
    private final JBList<ChatSession> sessionList;
    private final DefaultListModel<ChatSession> listModel;
    private final JButton newChatButton;
    private final SearchTextField searchField;
    private final List<ChatSession> allSessions = new ArrayList<>();
    private Consumer<ChatSession> onSessionSelected;
    private ChatSession currentSession;
    
    /**
     * 构造函数。
     */
    public SessionSelectorPanel() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty());
        
        // 顶部面板包含搜索框和新建按钮
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(JBUI.Borders.empty(10, 10, 5, 10));
        
        // 搜索框
        searchField = new SearchTextField();
        searchField.setBorder(JBUI.Borders.empty(0, 0, 5, 0));
        topPanel.add(searchField, BorderLayout.CENTER);
        
        // 新建按钮
        newChatButton = new JButton("新对话", AllIcons.General.Add);
        newChatButton.setBorder(JBUI.Borders.empty(2, 8));
        newChatButton.setFocusPainted(false);
        topPanel.add(newChatButton, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 会话列表
        listModel = new DefaultListModel<>();
        sessionList = new JBList<>(listModel);
        sessionList.setCellRenderer(new SessionListCellRenderer());
        sessionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JBScrollPane scrollPane = new JBScrollPane(sessionList);
        scrollPane.setBorder(JBUI.Borders.empty());
        
        add(scrollPane, BorderLayout.CENTER);
        
        // 初始化事件监听器
        initializeListeners();
    }
    
    /**
     * 初始化事件监听器。
     */
    private void initializeListeners() {
        // 搜索框文本变化监听
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                filterSessionList(searchField.getText());
            }
        });
        
        // 会话列表选择监听
        sessionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ChatSession selected = sessionList.getSelectedValue();
                if (selected != null && onSessionSelected != null) {
                    currentSession = selected;
                    onSessionSelected.accept(selected);
                }
            }
        });
        
        // 新建按钮点击事件
        newChatButton.addActionListener(e -> {
            ChatSession newSession = SessionManager.getInstance().createNewSession();
            refreshSessionList();
            sessionList.setSelectedValue(newSession, true);
            
            if (onSessionSelected != null) {
                currentSession = newSession;
                onSessionSelected.accept(newSession);
            }
        });
        
        // 会话项右键菜单
        sessionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = sessionList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        sessionList.setSelectedIndex(index);
                        showSessionContextMenu(e.getPoint());
                    }
                }
            }
        });
    }
    
    /**
     * 显示会话右键菜单。
     * @param point 鼠标位置
     */
    private void showSessionContextMenu(Point point) {
        ChatSession selected = sessionList.getSelectedValue();
        if (selected == null) return;
        
        JPopupMenu contextMenu = new JPopupMenu();
        
        // 重命名选项
        JMenuItem renameItem = new JMenuItem("重命名", AllIcons.Actions.Edit);
        renameItem.addActionListener(e -> {
            String newTitle = Messages.showInputDialog(
                    "输入新的会话标题", 
                    "重命名会话", 
                    Messages.getQuestionIcon(), 
                    selected.getTitle(), 
                    null);
            
            if (newTitle != null && !newTitle.trim().isEmpty()) {
                selected.setTitle(newTitle.trim());
                SessionManager.getInstance().saveSession(selected);
                refreshSessionList();
            }
        });
        
        // 清空会话选项
        JMenuItem clearItem = new JMenuItem("清空会话", AllIcons.Actions.GC);
        clearItem.addActionListener(e -> {
            int result = Messages.showYesNoDialog(
                    "确定要清空此会话中的所有消息吗？", 
                    "清空会话", 
                    Messages.getQuestionIcon());
            
            if (result == Messages.YES) {
                selected.clearMessages();
                SessionManager.getInstance().saveSession(selected);
                
                if (onSessionSelected != null) {
                    onSessionSelected.accept(selected);
                }
            }
        });
        
        // 删除选项
        JMenuItem deleteItem = new JMenuItem("删除会话", AllIcons.Actions.Cancel);
        deleteItem.addActionListener(e -> {
            int result = Messages.showYesNoDialog(
                    "确定要删除此会话吗？此操作不可撤销。", 
                    "删除会话", 
                    Messages.getWarningIcon());
            
            if (result == Messages.YES) {
                SessionManager.getInstance().deleteSession(selected.getId());
                refreshSessionList();
                
                // 如果删除的是当前会话，需要加载新的活动会话
                if (selected.equals(currentSession)) {
                    ChatSession newActive = SessionManager.getInstance().getActiveSession();
                    sessionList.setSelectedValue(newActive, true);
                    
                    if (onSessionSelected != null && newActive != null) {
                        currentSession = newActive;
                        onSessionSelected.accept(newActive);
                    }
                }
            }
        });
        
        contextMenu.add(renameItem);
        contextMenu.add(clearItem);
        contextMenu.addSeparator();
        contextMenu.add(deleteItem);
        
        contextMenu.show(sessionList, point.x, point.y);
    }
    
    /**
     * 根据搜索文本过滤会话列表。
     * @param searchText 搜索文本
     */
    private void filterSessionList(String searchText) {
        listModel.clear();
        
        if (searchText == null || searchText.trim().isEmpty()) {
            // 显示所有会话
            for (ChatSession session : allSessions) {
                listModel.addElement(session);
            }
        } else {
            // 过滤会话
            String searchLower = searchText.toLowerCase();
            for (ChatSession session : allSessions) {
                if (session.getTitle().toLowerCase().contains(searchLower)) {
                    listModel.addElement(session);
                }
            }
        }
    }
    
    /**
     * 刷新会话列表。
     */
    public void refreshSessionList() {
        // 保存当前选择
        ChatSession selected = sessionList.getSelectedValue();
        
        // 重新获取所有会话
        allSessions.clear();
        allSessions.addAll(SessionManager.getInstance().getAllSessions());
        
        // 重新填充列表
        filterSessionList(searchField.getText());
        
        // 恢复选择
        if (selected != null) {
            for (int i = 0; i < listModel.getSize(); i++) {
                if (listModel.getElementAt(i).getId().equals(selected.getId())) {
                    sessionList.setSelectedIndex(i);
                    break;
                }
            }
        } else if (currentSession != null) {
            // 尝试选择当前会话
            for (int i = 0; i < listModel.getSize(); i++) {
                if (listModel.getElementAt(i).getId().equals(currentSession.getId())) {
                    sessionList.setSelectedIndex(i);
                    break;
                }
            }
        } else if (listModel.getSize() > 0) {
            // 默认选择第一个
            sessionList.setSelectedIndex(0);
        }
    }
    
    /**
     * 设置会话选择回调。
     * @param onSessionSelected 会话选择回调
     */
    public void setOnSessionSelected(Consumer<ChatSession> onSessionSelected) {
        this.onSessionSelected = onSessionSelected;
    }
    
    /**
     * 会话列表项渲染器。
     */
    private static class SessionListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ChatSession) {
                ChatSession session = (ChatSession) value;
                label.setText(session.getTitle());
                label.setToolTipText(session.getDisplayDescription());
                label.setBorder(JBUI.Borders.empty(8, 12));
                label.setIcon(AllIcons.Actions.Forward);
            }
            
            // 添加底部边框，除了最后一项
            if (index < list.getModel().getSize() - 1) {
                label.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.ToolWindow.borderColor()),
                        JBUI.Borders.empty(8, 12)));
            }
            
            return label;
        }
    }
} 