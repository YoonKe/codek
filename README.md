# CodeK Assistant - IntelliJ IDEA AI编程助手

## 项目概述

CodeK Assistant是一个强大的IntelliJ IDEA插件，集成了先进的AI语言模型(LLMs)来辅助日常编程任务。该插件提供了一个直观的界面，让开发者能够通过自然语言与AI模型交互，获取代码建议，执行文件操作，以及理解和分析代码上下文。

## 主要功能

### 1. AI聊天界面
- 与多种AI模型进行实时对话（支持Claude、GPT等）
- Markdown渲染和代码语法高亮
- 会话管理和历史记录

### 2. 文件操作工具
- 读取文件内容（支持行范围）
- 写入和修改文件
- 创建新文件和目录

### 3. 代码辅助功能
- 智能代码建议和补全
- 代码分析和优化建议
- 上下文感知的编程帮助

### 4. 开发工作流集成
- 与IDE编辑器无缝集成
- 支持多种编程语言
- 自定义模型和API配置

## 文件操作工具详解

### 1. 读取文件 (readFile)

读取指定文件的内容，可以读取整个文件或指定行范围。

**参数:**
- `filePath`: 文件的绝对或相对路径（必需）
- `startLine`: 起始行号，从1开始计数（可选）
- `endLine`: 结束行号，从1开始计数（可选）

**示例:**
```json
{
  "filePath": "src/main/java/com/example/App.java",
  "startLine": 10,
  "endLine": 20
}
```

### 2. 写入文件 (writeFile)

向指定文件写入内容，可以替换整个文件或指定行范围的内容。

**参数:**
- `filePath`: 文件的绝对或相对路径（必需）
- `content`: 要写入的内容（必需）
- `startLine`: 起始行号，从1开始计数（可选）
- `endLine`: 结束行号，从1开始计数（可选）

**示例:**
```json
{
  "filePath": "src/main/java/com/example/App.java",
  "content": "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World!\");\n    }\n}",
  "startLine": 1,
  "endLine": 5
}
```

### 3. 创建文件 (createFile)

在指定路径创建新文件并写入内容。如果父目录不存在，将自动创建。

**参数:**
- `filePath`: 文件的绝对或相对路径（必需）
- `content`: 要写入的内容（必需）

**示例:**
```json
{
  "filePath": "src/main/java/com/example/NewClass.java",
  "content": "package com.example;\n\npublic class NewClass {\n    // 类内容\n}"
}
```

## 技术架构

### 核心组件

#### UI组件
- `CodekToolWindowFactory`: 在IDE中创建工具窗口
- `CodekToolWindowPanel`: 主UI面板
- `ChatPanel`: 聊天消息显示面板
- `SessionSelectorPanel`: 会话管理面板

#### 服务组件
- `LlmService`: AI模型通信服务
- `ToolExecutor`: 工具执行管理器
- `SessionManager`: 会话管理服务
- `CodeContextProvider`: 代码上下文提取器
- `EditorService`: 编辑器交互服务

### 依赖项

- OkHttp: HTTP请求处理
- Gson: JSON解析
- CommonMark/FlexMark: Markdown渲染
- IntelliJ Platform SDK: IDE集成

## 使用场景示例

1. **代码生成与重构**：描述需求，AI助手生成代码或提供重构建议

2. **问题排查**：分享错误信息，获取可能的解决方案

3. **学习新API**：询问如何使用特定库或框架的功能

4. **代码模板批处理**：读取模板文件，替换变量，创建新文件

5. **代码审查辅助**：分析代码并提供改进建议

## 配置说明

### API配置

1. 点击工具窗口顶部的配置按钮
2. 输入您的API密钥和API地址
3. 选择您偏好的AI模型

### 支持的模型

- Claude 3.5/3.7 系列
- GPT-4系列
- DeepSeek系列
- Gemini系列
- 其他自定义模型

## 注意事项

1. 所有文件路径都可以使用绝对路径或相对于项目根目录的相对路径
2. 写入和创建文件操作需要用户批准
3. 如果指定了行范围，系统会将内容替换为该范围内的文本；否则会替换整个文件内容
4. 使用创建文件工具时，如果文件已存在，将返回错误
5. 所有工具都会返回JSON格式的结果，包含操作状态和相关信息

## 未来计划

- 添加更多专业化工具
- 支持更多AI模型
- 增强代码分析能力
- 提供更多自定义选项

## 结语

CodeK Assistant旨在成为开发者的智能编程伙伴，通过AI技术提升编码效率和质量。无论是日常编码、学习新技术还是解决复杂问题，CodeK都能提供有价值的帮助。

如需进一步了解或获取支持，请参阅插件文档或联系维护团队。