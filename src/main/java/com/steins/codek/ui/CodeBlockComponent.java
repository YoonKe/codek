package com.steins.codek.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * 代码块组件，用于显示代码片段，支持语法高亮和应用代码到编辑器。
 * @author 0027013824
 */
public class CodeBlockComponent extends JBPanel<CodeBlockComponent> {
    private static final Logger LOG = Logger.getInstance(CodeBlockComponent.class);

    private final String filePath;
    private final int startLine; // 1-based
    private final int endLine;   // 1-based
    private final String codeToApply;
    private final Project project;
    private EditorEx editor;

    /**
     * 创建代码块组件。
     * @param project 当前项目
     * @param code 代码内容 (用于显示和应用)
     * @param filePath 文件路径
     * @param startLine 开始行 (1-based)
     * @param endLine 结束行 (1-based)
     */
    public CodeBlockComponent(@NotNull Project project, @NotNull String code, @NotNull String filePath, int startLine, int endLine) {
        this.project = project;
        this.codeToApply = code;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.compound(JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1), JBUI.Borders.empty(2)));
        
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        editor = createCodeViewer(code, filePath);
        add(editor.getComponent(), BorderLayout.CENTER);
        
        updatePreferredSize();
    }
    
    /**
     * 创建标题栏，显示文件名和跳转按钮。
     * @return 标题栏面板
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JBPanel<>();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBackground(UIUtil.getPanelBackground().darker());
        headerPanel.setBorder(JBUI.Borders.empty(4, 8));
        
        // 获取文件名
        String fileName = new File(filePath).getName();
        
        // 文件名标签
        JBLabel fileLabel = new JBLabel(fileName, AllIcons.FileTypes.Text, SwingConstants.LEFT);
        fileLabel.setForeground(UIUtil.getLabelForeground());
        fileLabel.setBorder(JBUI.Borders.emptyRight(8));
        
        // 行号信息
        JBLabel lineInfo = new JBLabel(String.format("行 %d-%d", startLine, endLine));
        lineInfo.setForeground(UIUtil.getLabelForeground());
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        
        // 跳转按钮
        JButton jumpButton = new JButton("跳转", AllIcons.Actions.EditSource);
        jumpButton.setToolTipText("在编辑器中打开并定位到该代码段");
        jumpButton.addActionListener(e -> jumpToSource());
        buttonPanel.add(jumpButton);
        
        // 应用代码按钮
        JButton applyButton = new JButton("应用", AllIcons.Actions.Commit);
        applyButton.setToolTipText("将此代码应用到文件中");
        applyButton.addActionListener(e -> applyCodeToFile());
        buttonPanel.add(applyButton);
        
        // 左侧容器
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(fileLabel);
        leftPanel.add(lineInfo);
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        
        // 给整个标题栏添加点击事件
        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    jumpToSource();
                }
            }
        });
        
        return headerPanel;
    }
    
    /**
     * 创建代码查看器 (只读 Editor)。
     */
    private EditorEx createCodeViewer(String code, String filePath) {
        EditorFactory editorFactory = EditorFactory.getInstance();
        Document viewerDocument = editorFactory.createDocument(code);
        EditorEx editor = (EditorEx) editorFactory.createViewer(viewerDocument, project);
        
        editor.getSettings().setLineNumbersShown(true);
        editor.getSettings().setFoldingOutlineShown(false);
        editor.getSettings().setAdditionalLinesCount(0);
        editor.getSettings().setAdditionalColumnsCount(0);
        editor.getSettings().setLineMarkerAreaShown(false);
        editor.getSettings().setIndentGuidesShown(false);
        editor.getSettings().setVirtualSpace(false);
        editor.getSettings().setWheelFontChangeEnabled(false);
        editor.getSettings().setUseSoftWraps(false); // 禁用自动换行，让滚动条出现
        
        editor.setCaretVisible(false);
        editor.setCaretEnabled(false);
        
        // 设置背景色为面板背景色
        editor.setBackgroundColor(getBackground());
        
        // 设置高亮
        FileType fileType = getFileType(filePath);
        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType);
        editor.setHighlighter(highlighter);
        
        return editor;
    }
    
    /**
     * 更新组件的首选大小以适应代码行数。
     */
    private void updatePreferredSize() {
        ApplicationManager.getApplication().invokeLater(() -> { // 确保在 EDT 线程
            if (editor == null || editor.isDisposed()) return;
            
            int lineCount = editor.getDocument().getLineCount();
            int lineHeight = editor.getLineHeight();
            // 加上标题栏高度和一些边距
            int headerHeight = getComponent(0).getPreferredSize().height;
            int totalHeight = Math.min(lineCount * lineHeight + headerHeight + JBUI.scale(15), JBUI.scale(350)); // 最大高度限制
            
            Dimension currentSize = getSize();
            setPreferredSize(new Dimension(currentSize.width > 0 ? currentSize.width : JBUI.scale(400), totalHeight)); // 设置首选大小
            
            // 请求重新布局父容器
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        });
    }

    private FileType getFileType(String filePath) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (virtualFile != null) {
            return virtualFile.getFileType();
        }
        // 备选方案：根据扩展名猜测
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            String extension = filePath.substring(lastDotIndex + 1);
            return FileTypeManager.getInstance().getFileTypeByExtension(extension);
        }
        return PlainTextFileType.INSTANCE; // 默认为纯文本
    }
    
    /**
     * 跳转到源代码。
     */
    private void jumpToSource() {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (virtualFile != null && virtualFile.isValid()) {
            // 打开文件并定位到起始行 (0-based index for descriptor)
            new OpenFileDescriptor(project, virtualFile, startLine - 1, 0).navigate(true);
        } else {
             Messages.showErrorDialog(project, "找不到文件或文件无效: " + filePath, "无法跳转");
        }
    }

    /**
     * 应用代码到文件。
     */
    private void applyCodeToFile() {
        File file = new File(filePath);
        if (!file.exists()) {
            showError("找不到文件: " + filePath);
            return;
        }
        
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (virtualFile == null) {
            showError("无法访问文件: " + filePath);
            return;
        }
        
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) {
            showError("无法获取文档对象: " + filePath);
            return;
        }
        
        try {
            int totalLines = document.getLineCount();
            if (startLine < 1 || endLine > totalLines) {
                showError("行号超出范围: " + startLine + "-" + endLine + ", 文件总行数: " + totalLines);
                return;
            }
            
            // 转换为0-based索引
            int zeroBasedStartLine = startLine - 1;
            int zeroBasedEndLine = endLine - 1;
            
            // 计算偏移量
            int startOffset = document.getLineStartOffset(zeroBasedStartLine);
            int endOffset = document.getLineEndOffset(zeroBasedEndLine);
            
            // 获取编辑器中显示的代码
            String codeToApply = editor.getDocument().getText();
            
            // 在写命令动作中执行文档修改
            WriteCommandAction.runWriteCommandAction(project, () -> {
                document.replaceString(startOffset, endOffset, codeToApply);
                FileDocumentManager.getInstance().saveDocument(document);
            });
            
            // 在编辑器中显示修改后的文件
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
            
            // 显示成功消息
            JOptionPane.showMessageDialog(
                this,
                "代码已成功应用到文件: " + filePath + " 的第 " + startLine + "-" + endLine + " 行",
                "应用成功",
                JOptionPane.INFORMATION_MESSAGE
            );
            
        } catch (IndexOutOfBoundsException e) {
            showError("计算行偏移量时出错: " + e.getMessage());
        } catch (Exception e) {
            showError("应用代码时出错: " + e.getMessage());
        }
    }
    
    /**
     * 显示错误消息。
     * @param message 错误消息
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "应用失败",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    /**
     * 释放资源。
     */
    public void dispose() {
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
            editor = null;
        }
    }
} 