package com.steins.codek.tool.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * CreateFileTool工具的单元测试类。
 * @author 0027013824
 */
public class CreateFileToolTest extends BasePlatformTestCase {
    private CreateFileTool createFileTool;
    private String tempDirPath;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Project project = getProject();
        createFileTool = new CreateFileTool(project);
        
        // 创建临时目录作为测试目录
        Path tempDir = Files.createTempDirectory("createFileToolTest");
        tempDirPath = tempDir.toString();
    }

    @Override
    protected void tearDown() throws Exception {
        // 清理临时目录
        deleteDirectory(new File(tempDirPath));
        super.tearDown();
    }

    /**
     * 测试创建新文件。
     */
    public void testCreateNewFile() {
        // 准备参数
        String fileName = "testNewFile.txt";
        String filePath = Paths.get(tempDirPath, fileName).toString();
        String content = "This is a test content.";
        
        Map<String, String> args = new HashMap<>();
        args.put("filePath", filePath);
        args.put("content", content);
        
        // 执行创建操作
        String result = createFileTool.execute(args);
        
        // 验证结果
        assertTrue("应返回成功结果", result.contains("\"success\": true"));
        assertTrue("文件应该被创建", Files.exists(Paths.get(filePath)));
        
        // 验证文件内容
        try {
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
            assertEquals("文件内容应该匹配", content, fileContent);
        }
        catch (Exception e) {
            fail("读取文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 测试创建已存在的文件。
     */
    public void testCreateExistingFile() throws Exception {
        // 先创建文件
        String fileName = "existingFile.txt";
        String filePath = Paths.get(tempDirPath, fileName).toString();
        Files.write(Paths.get(filePath), "Original content".getBytes());
        
        // 准备参数
        Map<String, String> args = new HashMap<>();
        args.put("filePath", filePath);
        args.put("content", "New content");
        
        // 执行创建操作
        String result = createFileTool.execute(args);
        
        // 验证结果
        assertTrue("应返回错误结果", result.contains("\"error\""));
        assertTrue("应提示文件已存在", result.contains("File already exists"));
    }

    /**
     * 测试创建包含父目录的文件。
     */
    public void testCreateFileWithParentDirectories() {
        // 准备参数
        String filePath = Paths.get(tempDirPath, "subdir1", "subdir2", "newFile.txt").toString();
        String content = "Content in nested directory";
        
        Map<String, String> args = new HashMap<>();
        args.put("filePath", filePath);
        args.put("content", content);
        
        // 执行创建操作
        String result = createFileTool.execute(args);
        
        // 验证结果
        assertTrue("应返回成功结果", result.contains("\"success\": true"));
        assertTrue("文件应该被创建", Files.exists(Paths.get(filePath)));
        
        // 验证文件内容
        try {
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
            assertEquals("文件内容应该匹配", content, fileContent);
        }
        catch (Exception e) {
            fail("读取文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 测试缺少必需参数。
     */
    public void testMissingRequiredParameters() {
        // 准备参数 - 缺少filePath
        Map<String, String> args = new HashMap<>();
        args.put("content", "Some content");
        
        // 执行创建操作
        String result = createFileTool.execute(args);
        
        // 验证结果
        assertTrue("应返回错误结果", result.contains("\"error\""));
        assertTrue("应提示缺少参数", result.contains("Missing required parameter"));
    }

    /**
     * 递归删除目录及其内容。
     */
    private void deleteDirectory(@NotNull File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
} 