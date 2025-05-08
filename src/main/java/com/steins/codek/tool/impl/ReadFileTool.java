package com.steins.codek.tool.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
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
 * 读取文件内容的工具实现。
 * @author 0027013824
 */
public class ReadFileTool implements Tool {
    private static final Logger LOG = Logger.getInstance(ReadFileTool.class);
    private static final int MAX_READ_LINES = 500; // 限制一次最多读取的行数

    private final Project project;

    public ReadFileTool(Project project) {
        this.project = project;
    }

    @Override
    public String getName() {
        return "readFile";
    }

    @Override
    public String getDescription() {
        return "Reads the content of a specified file. Can read the entire file or a specific range of lines.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        // 使用正确的 ToolParameter 构造函数
        return Arrays.asList(
                new ToolParameter("filePath", "The absolute or relative path to the file.", true),
                new ToolParameter("startLine", "The 1-based starting line number (inclusive). Optional.", false),
                new ToolParameter("endLine", "The 1-based ending line number (inclusive). Optional.", false)
        );
    }

    @Override
    public String execute(Map<String, String> arguments) {
        String filePath = arguments.get("filePath");
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
        
        final Integer finalStartLine = startLine;
        final Integer finalEndLine = endLine;
        final String finalFilePath = filePath.trim();
        final String originalStartLineStr = startLineStr;
        final String originalEndLineStr = endLineStr;

        return ReadAction.compute(() -> {
            VirtualFile virtualFile = findVirtualFile(finalFilePath);
            if (virtualFile == null || !virtualFile.exists()) {
                return String.format("{\"error\": \"File not found: %s\"}", escapeJson(finalFilePath));
            }
            if (virtualFile.isDirectory()) {
                return String.format("{\"error\": \"Path is a directory, not a file: %s\"}", escapeJson(finalFilePath));
        }

            Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (document == null) {
                return String.format("{\"error\": \"Could not get document for file: %s\"}", escapeJson(finalFilePath));
            }

            try {
                String content;
                int totalLines = document.getLineCount();
                Integer currentEndLine = finalEndLine;

                if (finalStartLine != null && currentEndLine != null) {
                    if (finalStartLine < 1 || currentEndLine < finalStartLine || finalStartLine > totalLines) {
                        return String.format("{\"error\": \"Invalid line numbers: startLine=%d, endLine=%d, totalLines=%d\"}",
                                finalStartLine, currentEndLine, totalLines);
                    }
                    int actualEndLine = currentEndLine;
                    if (actualEndLine - finalStartLine + 1 > MAX_READ_LINES) {
                         actualEndLine = finalStartLine + MAX_READ_LINES - 1;
                         if (actualEndLine > totalLines) {
                             actualEndLine = totalLines;
                         }
                         LOG.warn(String.format("Reading range truncated due to limit. Original: %s-%s, Reading: %d-%d for file %s",
                                   originalStartLineStr, originalEndLineStr, finalStartLine, actualEndLine, finalFilePath));
                    }
                    
                    int zeroBasedStartLine = finalStartLine - 1;
                    int zeroBasedEndLine = actualEndLine - 1;
                    if (zeroBasedStartLine < 0 || zeroBasedEndLine < zeroBasedStartLine || zeroBasedStartLine >= totalLines) {
                         return String.format("{\"error\": \"Calculated invalid zero-based line numbers: startLine=%d, endLine=%d, totalLines=%d\"}",
                                zeroBasedStartLine + 1, zeroBasedEndLine + 1, totalLines);
                    }

                    int startOffset = document.getLineStartOffset(zeroBasedStartLine);
                    int endOffset = document.getLineEndOffset(zeroBasedEndLine);

                    content = document.getText(new TextRange(startOffset, endOffset));
                    return String.format("{\"filePath\": \"%s\", \"startLine\": %d, \"endLine\": %d, \"content\": \"%s\"}",
                                        escapeJson(finalFilePath), finalStartLine, actualEndLine, escapeJson(content));

                } else {
                    if (totalLines == 0) {
                        content = "";
                    } else {
                        int linesToRead = Math.min(totalLines, MAX_READ_LINES);
                        int endOffset = document.getLineEndOffset(Math.max(0, linesToRead - 1));
                        content = document.getText(new TextRange(0, endOffset));
                    }
                    String warning = totalLines > MAX_READ_LINES ? String.format("File truncated to first %d lines.", MAX_READ_LINES) : "";
                    return String.format("{\"filePath\": \"%s\", \"content\": \"%s\", \"warning\": \"%s\"}",
                                        escapeJson(finalFilePath), escapeJson(content), escapeJson(warning));
                }

            } catch (IndexOutOfBoundsException e) {
                LOG.error("Error calculating offsets for file reading for file: " + finalFilePath, e);
                return String.format("{\"error\": \"Error reading file lines: Invalid line numbers calculated. Total lines: %d\"}", document.getLineCount());
            } catch (Exception e) {
                LOG.error("Error reading file: " + finalFilePath, e);
                return String.format("{\"error\": \"An unexpected error occurred while reading the file: %s\"}", escapeJson(e.getMessage()));
            }
        });
    }

    private VirtualFile findVirtualFile(String filePath) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (file != null) return file;

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