package com.steins.codek.tool.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.steins.codek.tool.Tool;
import com.steins.codek.tool.ToolParameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 创建新文件的工具实现。
 * @author 0027013824
 */
public class CreateFileTool implements Tool {
    private static final Logger LOG = Logger.getInstance(CreateFileTool.class);

    private final Project project;

    public CreateFileTool(Project project) {
        this.project = project;
    }

    @Override
    public String getName() {
        return "createFile";
    }

    @Override
    public String getDescription() {
        return "Creates a new file at the specified path with the provided content. Will create parent directories if they don't exist.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("filePath", "string", "The absolute or relative path to the file to create.", true),
                new ToolParameter("content", "string", "The content to write to the file.", true)
        );
    }

    @Override
    public boolean requiresApproval() {
        return true; // 创建文件操作需要用户批准
    }

    @Override
    public String execute(Map<String, String> arguments) {
        String filePath = arguments.get("filePath");
        String content = arguments.get("content");

        if (filePath == null || filePath.trim().isEmpty()) {
            return "{\"error\": \"Missing required parameter: filePath\"}";
        }
        
        if (content == null) {
            content = ""; // 允许创建空文件
        }
        
        final String finalFilePath = filePath.trim();
        final String finalContent = content;

        try {
            return ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    // 检查文件是否已存在
                    File file = new File(finalFilePath);
                    if (file.exists()) {
                        return String.format("{\"error\": \"File already exists: %s\"}", escapeJson(finalFilePath));
                    }

                    // 确保父目录存在
                    Path parentDir = Paths.get(finalFilePath).getParent();
                    if (parentDir != null) {
                        Files.createDirectories(parentDir);
                    }

                    // 创建空文件以便后续写入内容
                    Files.createFile(Paths.get(finalFilePath));
                    
                    // 刷新VFS以检测新创建的文件
                    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
                            finalFilePath.replace('\\', '/'));
                    
                    if (virtualFile == null) {
                        return String.format("{\"error\": \"File was created but could not be found in VFS: %s\"}", 
                                escapeJson(finalFilePath));
                    }

                    // 使用WriteCommandAction写入内容
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        try {
                            virtualFile.setBinaryContent(finalContent.getBytes());
                        }
                        catch (IOException e) {
                            LOG.error("Error writing content to newly created file: " + finalFilePath, e);
                        }
                    });

                    // 构建返回结果
                    return String.format(
                            "{\"success\": true, \"filePath\": \"%s\", \"message\": \"File created successfully\"}",
                            escapeJson(finalFilePath));
                }
                catch (IOException e) {
                    LOG.error("Error creating file: " + finalFilePath, e);
                    return String.format("{\"error\": \"Failed to create file: %s\"}", escapeJson(e.getMessage()));
                }
            }).get(); // 等待异步操作完成
        }
        catch (Exception e) {
            LOG.error("Error executing CreateFileTool", e);
            return String.format("{\"error\": \"Failed to execute create operation: %s\"}", escapeJson(e.getMessage()));
        }
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