package com.steins.codek.tool.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.steins.codek.tool.Tool;
import com.steins.codek.tool.ToolParameter;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 写入文件内容的工具实现。
 * @author 0027013824
 */
public class WriteFileTool implements Tool {
    private static final Logger LOG = Logger.getInstance(WriteFileTool.class);

    private final Project project;

    public WriteFileTool(Project project) {
        this.project = project;
    }

    @Override
    public String getName() {
        return "writeFile";
    }

    @Override
    public String getDescription() {
        return "Writes content to a specified file. Can replace the entire file or a specific range of lines.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("filePath", "string", "The absolute or relative path to the file.", true),
                new ToolParameter("content", "string", "The content to write to the file.", true),
                new ToolParameter("startLine", "integer", "The 1-based starting line number (inclusive). Optional.", false),
                new ToolParameter("endLine", "integer", "The 1-based ending line number (inclusive). Optional.", false)
        );
    }

    @Override
    public boolean requiresApproval() {
        return true; // 写入文件操作需要用户批准
    }

    @Override
    public String execute(Map<String, String> arguments) {
        String filePath = arguments.get("filePath");
        String content = arguments.get("content");
        String startLineStr = arguments.get("startLine");
        String endLineStr = arguments.get("endLine");

        Integer startLine = null;
        Integer endLine = null;

        try {
            if (startLineStr != null && !startLineStr.trim().isEmpty()) {
                startLine = Integer.parseInt(startLineStr.trim());
            }
            if (endLineStr != null && !endLineStr.trim().isEmpty()) {
                endLine = Integer.parseInt(endLineStr.trim());
            }
        } catch (NumberFormatException e) {
            return String.format("{\"error\": \"Invalid number format for startLine or endLine: %s, %s\"}", 
                                escapeJson(startLineStr), escapeJson(endLineStr));
        }

        if (filePath == null || filePath.trim().isEmpty()) {
            return "{\"error\": \"Missing required parameter: filePath\"}";
        }
        
        if (content == null) {
            content = ""; // 允许清空文件内容
        }
        
        final Integer finalStartLine = startLine;
        final Integer finalEndLine = endLine;
        final String finalFilePath = filePath.trim();
        final String finalContent = content;

        try {
            return ApplicationManager.getApplication().executeOnPooledThread(() -> {
                VirtualFile virtualFile = findVirtualFile(finalFilePath);
                if (virtualFile == null) {
                    return String.format("{\"error\": \"File not found: %s\"}", escapeJson(finalFilePath));
                }
                if (virtualFile.isDirectory()) {
                    return String.format("{\"error\": \"Path is a directory, not a file: %s\"}", escapeJson(finalFilePath));
                }

                try {
                    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
                    if (document == null) {
                        return String.format("{\"error\": \"Could not get document for file: %s\"}", escapeJson(finalFilePath));
                    }

                    // 计算要替换的范围
                    int totalLines = document.getLineCount();
                    int startOffset = 0;
                    int endOffset = document.getTextLength();

                    if (finalStartLine != null && finalEndLine != null) {
                        if (finalStartLine < 1 || finalEndLine < finalStartLine || finalStartLine > totalLines) {
                            return String.format("{\"error\": \"Invalid line numbers: startLine=%d, endLine=%d, totalLines=%d\"}",
                                    finalStartLine, finalEndLine, totalLines);
                        }

                        // 转换为0-based索引
                        int zeroBasedStartLine = finalStartLine - 1;
                        int zeroBasedEndLine = Math.min(finalEndLine - 1, totalLines - 1);

                        // 计算偏移量
                        startOffset = document.getLineStartOffset(zeroBasedStartLine);
                        endOffset = document.getLineEndOffset(zeroBasedEndLine);
                    }

                    // 在写命令动作中执行文档修改
                    final int finalStartOffset = startOffset;
                    final int finalEndOffset = endOffset;
                    
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        document.replaceString(finalStartOffset, finalEndOffset, finalContent);
                        FileDocumentManager.getInstance().saveDocument(document);
                    });

                    // 构建返回结果
                    String result;
                    if (finalStartLine != null && finalEndLine != null) {
                        result = String.format(
                                "{\"success\": true, \"filePath\": \"%s\", \"startLine\": %d, \"endLine\": %d, \"message\": \"Content written to specific lines\"}",
                                escapeJson(finalFilePath), finalStartLine, finalEndLine);
                    } else {
                        result = String.format(
                                "{\"success\": true, \"filePath\": \"%s\", \"message\": \"Content written to entire file\"}",
                                escapeJson(finalFilePath));
                    }
                    return result;

                } catch (IndexOutOfBoundsException e) {
                    LOG.error("Error calculating offsets for file writing: " + finalFilePath, e);
                    return String.format("{\"error\": \"Error calculating line offsets: %s\"}", escapeJson(e.getMessage()));
                } catch (Exception e) {
                    LOG.error("Error writing to file: " + finalFilePath, e);
                    return String.format("{\"error\": \"An unexpected error occurred while writing to the file: %s\"}", escapeJson(e.getMessage()));
                }
            }).get(); // 等待异步操作完成
        } catch (Exception e) {
            LOG.error("Error executing WriteFileTool", e);
            return String.format("{\"error\": \"Failed to execute write operation: %s\"}", escapeJson(e.getMessage()));
        }
    }

    private VirtualFile findVirtualFile(String filePath) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) return file;

        // 检查相对路径
        if (project != null && project.getBasePath() != null) {
            String basePath = project.getBasePath();
            String absolutePath1 = basePath + File.separator + filePath;
            String absolutePath2 = basePath + "/" + filePath;
            
            file = LocalFileSystem.getInstance().findFileByPath(absolutePath1.replace(File.separatorChar, '/'));
            if (file != null) return file;
            
            file = LocalFileSystem.getInstance().findFileByPath(absolutePath2.replace(File.separatorChar, '/'));
            if (file != null) return file;
            
            File ioFile = new File(basePath, filePath);
            if (ioFile.exists()) {
                file = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
                if (file != null) return file;
            }
        }
        
        // 尝试作为绝对路径
        File ioFileDirect = new File(filePath);
        if (ioFileDirect.exists()) {
             file = LocalFileSystem.getInstance().findFileByIoFile(ioFileDirect);
             if (file != null) return file;
        }

        LOG.warn("Could not find VirtualFile for path: " + filePath);
        return null;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '/': sb.append("\\/"); break;
                default:
                    if (c <= '\u001F') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
} 