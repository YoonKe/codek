package com.steins.codek.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器服务类，用于获取当前编辑器的代码上下文。
 * @author 0027013824
 */
public class EditorService {
    private static final Logger LOG = Logger.getInstance(EditorService.class);
    private final Project project;

    /**
     * 构造函数。
     * @param project 当前项目。
     */
    public EditorService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * 获取当前打开的编辑器。
     * @return 当前编辑器，如果没有打开的编辑器则返回null。
     */
    @Nullable
    public Editor getCurrentEditor() {
        return ReadAction.compute(() -> 
            FileEditorManager.getInstance(project).getSelectedTextEditor()
        );
    }

    /**
     * 获取当前选中的代码。
     * @return 选中的代码文本，如果没有选中则返回null。
     */
    @Nullable
    public String getSelectedCode() {
        return ReadAction.compute(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return null;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
            return selectionModel.getSelectedText();
        }
        return null;
        });
    }

    /**
     * 获取当前打开文件的全部内容。
     * @return 文件内容，如果没有打开文件则返回null。
     */
    @Nullable
    public String getCurrentFileContent() {
        return ReadAction.compute(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return null;
        }
        return editor.getDocument().getText();
        });
    }

    /**
     * 获取当前打开文件的名称。
     * @return 文件名，如果没有打开文件则返回null。
     */
    @Nullable
    public String getCurrentFileName() {
        return ReadAction.compute(() -> {
            VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
            VirtualFile file = files.length > 0 ? files[0] : null;
        return file != null ? file.getName() : null;
        });
    }

    /**
     * 获取当前打开文件的路径。
     * @return 文件路径，如果没有打开文件则返回null。
     */
    @Nullable
    public String getCurrentFilePath() {
        return ReadAction.compute(() -> {
            VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
            VirtualFile file = files.length > 0 ? files[0] : null;
        return file != null ? file.getPath() : null;
        });
    }

    /**
     * 获取当前文件的语言（基于文件扩展名）。
     * @return 语言标识符，如果无法确定则返回"text"。
     */
    @NotNull
    public String getCurrentFileLanguage() {
        return ReadAction.compute(() -> {
            VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
            VirtualFile file = files.length > 0 ? files[0] : null;
        if (file == null) {
            return "text";
        }
        
        String extension = file.getExtension();
        if (extension == null) {
            return "text";
        }
        
        // 简单映射，可以根据需要扩展
        switch (extension.toLowerCase()) {
            case "java": return "java";
            case "kt": return "kotlin";
            case "js": return "javascript";
            case "ts": return "typescript";
            case "py": return "python";
            case "html": return "html";
            case "css": return "css";
            case "json": return "json";
            case "xml": return "xml";
            case "md": return "markdown";
            default: return extension.toLowerCase();
        }
        });
    }
    
    /**
     * 获取当前光标位置上下文代码（前后各10行）。
     * @return 上下文代码，如果没有打开文件则返回null。
     */
    @Nullable
    public String getCurrentCursorContext() {
        return ReadAction.compute(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return null;
        }
        
        int caretOffset = editor.getCaretModel().getOffset();
        int lineNumber = editor.getDocument().getLineNumber(caretOffset);
        int startLine = Math.max(0, lineNumber - 10);
        int endLine = Math.min(editor.getDocument().getLineCount() - 1, lineNumber + 10);
        
        int startOffset = editor.getDocument().getLineStartOffset(startLine);
        int endOffset = editor.getDocument().getLineEndOffset(endLine);
        
        return editor.getDocument().getText().substring(startOffset, endOffset);
        });
    }
} 