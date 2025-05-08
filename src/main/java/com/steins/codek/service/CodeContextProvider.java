package com.steins.codek.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.steins.codek.tool.Tool;
import com.steins.codek.tool.ToolParameter;
import com.steins.codek.service.ToolExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码上下文提供者，负责收集当前代码环境信息并组织为上下文。
 * @author 0027013824
 */
public class CodeContextProvider {
    private static final Logger LOG = Logger.getInstance(CodeContextProvider.class);
    private final Project project;
    private final EditorService editorService;
    private final ToolExecutor toolExecutor;

    /**
     * 构造函数。
     * @param project 当前项目。
     * @param toolExecutor 工具执行器实例。
     */
    public CodeContextProvider(@NotNull Project project, @NotNull ToolExecutor toolExecutor) {
        this.project = project;
        this.editorService = new EditorService(project);
        this.toolExecutor = toolExecutor;
    }

    /**
     * 获取当前编辑器的代码上下文信息。
     * @return 包含所有上下文信息的Map。
     */
    @NotNull
    public Map<String, String> getCurrentContext() {
        Map<String, String> context = new HashMap<>();

        // 获取当前文件信息
        String fileName = editorService.getCurrentFileName();
        String filePath = editorService.getCurrentFilePath();
        String fileLanguage = editorService.getCurrentFileLanguage();
        String selectedCode = editorService.getSelectedCode();
        String cursorContext = editorService.getCurrentCursorContext();

        // 放入上下文Map
        if (fileName != null) {
            context.put("fileName", fileName);
        }
        if (filePath != null) {
            context.put("filePath", filePath);
        }
        context.put("language", fileLanguage);
        if (selectedCode != null) {
            context.put("selectedCode", selectedCode);
        }
        if (cursorContext != null) {
            context.put("cursorContext", cursorContext);
        }

        // 添加项目信息
        context.put("projectName", project.getName());
        String projectJdkName = ProjectRootManager.getInstance(project).getProjectSdkName();
        if (projectJdkName != null) {
            context.put("projectJdk", projectJdkName);
        }

        return context;
    }

    /**
     * 根据当前上下文和可用工具创建系统提示信息。
     * @return 系统提示信息。
     */
    @NotNull
    public String createSystemPrompt() {
        Map<String, String> context = getCurrentContext();
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个基于 IntelliJ IDEA 的智能编程助手 CodeK，根据以下上下文和可用工具提供编程帮助。\n\n");

        // 添加项目信息
        prompt.append("## 项目信息：\n");
        prompt.append("- 项目名称：").append(context.getOrDefault("projectName", "未知")).append("\n");
        if (context.containsKey("projectJdk")) {
            prompt.append("- 项目JDK：").append(context.get("projectJdk")).append("\n");
        }

        // 添加文件信息
        if (context.containsKey("fileName")) {
            prompt.append("\n## 当前文件：\n");
            prompt.append("- 文件名：").append(context.get("fileName")).append("\n");
            if (context.containsKey("filePath")) {
                prompt.append("- 文件路径：").append(context.get("filePath")).append("\n");
            }
            prompt.append("- 语言：").append(context.get("language")).append("\n");
        }

        // 如果有选中的代码，添加到提示中
        if (context.containsKey("selectedCode")) {
            prompt.append("\n## 用户选中的代码：\n```").append(context.get("language")).append("\n");
            prompt.append(context.get("selectedCode")).append("\n```\n");
        }
        // 如果有光标上下文，但没有选中代码，添加光标上下文
        else if (context.containsKey("cursorContext")) {
            prompt.append("\n## 光标位置上下文代码：\n```").append(context.get("language")).append("\n");
            prompt.append(context.get("cursorContext")).append("\n```\n");
        }

        // 添加可用工具描述
        prompt.append("\n## 可用工具：\n");
        prompt.append("你可以使用以下工具来帮助完成任务。请使用指定的 XML 格式调用工具：\n");
        for (Tool tool : toolExecutor.getAvailableTools()) {
            prompt.append(tool.getDescription()).append("\n\n");
        }

        prompt.append("\n## 指令：\n");
        prompt.append("请根据上述上下文和可用工具，提供简洁、准确的帮助。如果需要使用工具，请按格式输出工具调用。");

        return prompt.toString();
    }
    
    /**
     * 获取上下文摘要（用于显示在聊天窗口）。
     * @return 上下文摘要字符串。
     */
    @NotNull
    public String getContextSummary() {
        Map<String, String> context = getCurrentContext();
        StringBuilder summary = new StringBuilder();
        
        if (context.containsKey("fileName")) {
            summary.append("文件：").append(context.get("fileName"));
            if (context.containsKey("selectedCode")) {
                summary.append(" (已选中代码)");
            }
        } else {
            summary.append("无活动文件");
        }
        
        return summary.toString();
    }
    
    /**
     * 获取当前代码上下文的文本表示。
     * 包括当前文件、选中代码或光标上下文等信息。
     * @return 代码上下文的文本表示。
     */
    @NotNull
    public String getContext() {
        Map<String, String> context = getCurrentContext();
        StringBuilder text = new StringBuilder();
        
        if (context.containsKey("fileName")) {
            text.append("## 当前文件：").append(context.get("fileName")).append("\n");
            
            if (context.containsKey("filePath")) {
                text.append("## 文件路径：").append(context.get("filePath")).append("\n");
            }
            
            text.append("## 语言：").append(context.get("language")).append("\n\n");
            
            // 添加选中的代码或光标上下文
            if (context.containsKey("selectedCode")) {
                text.append("## 选中的代码：\n");
                text.append(context.get("selectedCode")).append("\n");
            } else if (context.containsKey("cursorContext")) {
                text.append("## 当前光标位置的代码上下文：\n");
                text.append(context.get("cursorContext")).append("\n");
            }
        } else {
            text.append("无活动文件");
        }
        
        return text.toString();
    }
} 