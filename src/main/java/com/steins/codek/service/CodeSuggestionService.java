package com.steins.codek.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 代码建议服务类，负责将LLM的建议应用到代码编辑器中。
 * @author 0027013824
 */
public class CodeSuggestionService {
    private static final Logger LOG = Logger.getInstance(CodeSuggestionService.class);
    private final Project project;
    private final EditorService editorService;

    /**
     * 构造函数。
     * @param project 当前项目。
     */
    public CodeSuggestionService(@NotNull Project project) {
        this.project = project;
        this.editorService = new EditorService(project);
    }

    /**
     * 将代码建议应用到当前选中的代码区域。
     * @param suggestion 代码建议。
     * @return 是否成功应用。
     */
    public boolean applySuggestionToSelection(@NotNull String suggestion) {
        Editor editor = editorService.getCurrentEditor();
        if (editor == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("无法应用代码建议：没有活动的编辑器");
            }
            return false;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("无法应用代码建议：没有选中的代码");
            }
            return false;
        }

        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();
        Document document = editor.getDocument();

        // 使用WriteCommandAction在可撤销的命令中执行文档修改
        WriteCommandAction.runWriteCommandAction(project, "应用代码建议", null, () -> {
            document.replaceString(startOffset, endOffset, suggestion);
        });

        return true;
    }
    
    /**
     * 在光标位置插入代码建议。
     * @param suggestion 代码建议。
     * @return 是否成功插入。
     */
    public boolean insertSuggestionAtCursor(@NotNull String suggestion) {
        Editor editor = editorService.getCurrentEditor();
        if (editor == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("无法插入代码建议：没有活动的编辑器");
            }
            return false;
        }

        int cursorOffset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();

        // 使用WriteCommandAction在可撤销的命令中执行文档修改
        WriteCommandAction.runWriteCommandAction(project, "插入代码建议", null, () -> {
            document.insertString(cursorOffset, suggestion);
        });

        return true;
    }
    
    /**
     * 从LLM响应中提取代码块。
     * 检测包含在```标记之间的代码。
     * @param llmResponse LLM的响应文本。
     * @return 提取的代码块，如果没有找到则返回null。
     */
    @Nullable
    public String extractCodeBlockFromResponse(@NotNull String llmResponse) {
        // 查找第一个代码块标记
        int codeBlockStart = llmResponse.indexOf("```");
        if (codeBlockStart == -1) {
            return null;
        }
        
        // 查找代码块语言标记（可选）
        int lineBreakAfterStart = llmResponse.indexOf('\n', codeBlockStart);
        if (lineBreakAfterStart == -1) {
            return null;
        }
        
        // 查找结束标记
        int codeBlockEnd = llmResponse.indexOf("```", lineBreakAfterStart);
        if (codeBlockEnd == -1) {
            return null;
        }
        
        // 提取代码块，不包括语言标记行
        String codeBlock = llmResponse.substring(lineBreakAfterStart + 1, codeBlockEnd).trim();
        return codeBlock;
    }
    
    /**
     * 检查LLM响应是否包含代码块。
     * @param llmResponse LLM的响应文本。
     * @return 是否包含代码块。
     */
    public boolean containsCodeBlock(@NotNull String llmResponse) {
        return llmResponse.contains("```") && llmResponse.indexOf("```") != llmResponse.lastIndexOf("```");
    }
    
    /**
     * 从LLM响应中提取代码。
     * 这是一个简化的包装方法，保持与新接口兼容。
     * @param content LLM的响应内容。
     * @return 提取的代码，如果没有找到则返回null。
     */
    @Nullable
    public String extractCode(@NotNull String content) {
        return extractCodeBlockFromResponse(content);
    }
    
    /**
     * 将代码应用到编辑器。
     * 首先尝试应用到选区，如果没有选区则插入到光标位置。
     * @param code 要应用的代码。
     * @return 是否成功应用。
     */
    public boolean applyCodeToEditor(@NotNull String code) {
        // 尝试应用到选区
        boolean applied = applySuggestionToSelection(code);
        if (!applied) {
            // 如果没有选区，尝试插入到光标位置
            applied = insertSuggestionAtCursor(code);
        }
        return applied;
    }
} 