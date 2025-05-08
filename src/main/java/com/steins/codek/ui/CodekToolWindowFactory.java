package com.steins.codek.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * CodeK 工具窗口工厂类，用于创建工具窗口 UI。
 * 现在创建现代化的UI面板，采用白色、灰色、蓝色、淡紫色组合。
 * @author 0027013824
 */
public class CodekToolWindowFactory implements ToolWindowFactory {

    /**
     * 当 Tool Window 首次被打开时调用，用于创建窗口内容。
     *
     * @param project    当前项目。
     * @param toolWindow 当前工具窗口实例。
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 1. 创建现代化 UI 面板实例
        ModernCodekToolWindowPanel toolWindowPanel = new ModernCodekToolWindowPanel(project, toolWindow);

        // 2. 获取 ContentFactory 实例
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 3. 使用 UI 面板创建 Content
        // 第一个参数是 UI 组件，第二个参数是 Tab 显示的名称（null 表示不显示 Tab），第三个参数表示是否可关闭
        Content content = contentFactory.createContent(toolWindowPanel.getContent(), null, false);

        // 4. 将 Content 添加到 Tool Window 中
        toolWindow.getContentManager().addContent(content);
    }
}