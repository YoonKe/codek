package com.steins.codek.prompt;

import com.steins.codek.tool.Tool;
import com.steins.codek.tool.ToolParameter;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 负责组装发送给 LLM 的系统提示词。
 * @author 0027013824
 */
public class SystemPromptAssembler {

    private static final String BASE_ROLE = "You are CodeK, an AI programming assistant integrated into the IntelliJ IDEA IDE.";
    
    private static final String TOOL_USAGE_GUIDELINES = """
## Tool Usage Guidelines
- You have access to a set of tools to interact with the user's IDE and environment.
- To use a tool, respond with a JSON object within a `tool_calls` block in your message. The JSON object should specify the tool name and its arguments.
- Example of requesting the 'readFile' tool:
```json
"tool_calls": [
  {
    "id": "call_abc123", // Generate a unique ID for each call
    "type": "function",
    "function": {
      "name": "readFile",
      "arguments": "{\\\"filePath\\\": \\\"src/main/java/com/example/MyClass.java\\\"}" // Arguments MUST be a JSON string escaped within the outer JSON string
    }
  }
]
```
- The system will execute the tool and return the result to you in a subsequent 'tool' role message with the corresponding `tool_call_id`.
- Use tools proactively when you need information or to perform actions based on the conversation.
- Only use the tools provided below. Do not try to guess tool names or parameters. Do not invent tools.
""";

    private static final String CODE_OUTPUT_FORMAT_INSTRUCTION = """
## Code Output Format for Modifications
- When you provide code modifications that should be applied directly to a file, you **MUST** use the following specific fenced code block format:

```startLine:endLine:filePath
// Your modified code block here
```

- `startLine`: The **1-based** line number where the replacement should **begin** in the original file.
- `endLine`: The **1-based** line number where the replacement should **end** in the original file (inclusive).
- `filePath`: The absolute or relative path to the file that should be modified (relative to the project root).
- **Crucially**: The code block MUST start immediately after the `filePath` on a new line, and the entire block must end with ``` on its own line.
- Only use this exact format for code intended to be directly applied by the 'Apply to Editor' action. For general code examples or explanations, use standard Markdown code blocks (e.g., ```java ... ```).
- Example:

```5:10:/src/main/java/com/example/Utils.java
    public static void newUtilityMethod() {
        // New implementation
        System.out.println("This is the new method content.");
    }
```

- **Important**: If you only need to insert code without replacing existing lines, set `endLine` to be `startLine - 1`. For example, to insert code before line 5: ````4:4:/path/to/file.java ... ````
- If you need to append code to the end of the file, use a large line number for both `startLine` and `endLine` that is guaranteed to be beyond the current end of the file, e.g., ````99999:99999:/path/to/file.java ... ````
""";

    private static final String GENERAL_RULES = """
## General Rules
- Be concise and helpful.
- Answer truthfully based on the provided context and tool results.
- If you don't know the answer or a tool fails, state that clearly.
- Format your responses using Markdown.
- Adhere strictly to the specified formats for tool calls and code modification blocks.
- Include the current date and time in your responses when relevant (e.g., when providing status updates or logging events).
""";

    /**
     * 组装完整的系统提示词。
     *
     * @param availableTools      可用的工具列表。
     * @param customInstructions  用户自定义的额外指令 (可选)。
     * @return 组装好的系统提示词字符串。
     */
    public static String assemblePrompt(@Nullable List<Tool> availableTools, @Nullable String customInstructions) {
        StringBuilder prompt = new StringBuilder();

        // 1. 基本角色定义
        prompt.append(BASE_ROLE).append("\n\n");

        // 2. 工具使用指南
        prompt.append(TOOL_USAGE_GUIDELINES).append("\n\n");

        // 3. 可用工具列表
        prompt.append("## Available Tools\n");
        if (availableTools != null && !availableTools.isEmpty()) {
            for (Tool tool : availableTools) {
                prompt.append("### `").append(tool.getName()).append("`\n");
                prompt.append(tool.getDescription()).append("\n");
                // 假设 ToolParameter 有 getType() 和 isRequired() 方法
                if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                    prompt.append("Parameters:\n");
                    for (ToolParameter param : tool.getParameters()) {
                        prompt.append("- `").append(param.getName()).append("`: (")
                              .append(param.getType() != null ? param.getType() : "string") // 显示类型，默认为 string
                              .append(param.isRequired() ? ", required" : ", optional") // 显示是否必需
                              .append(") - ").append(param.getDescription()).append("\n");
                    }
                    prompt.append("\n"); // 在每个工具后加空行
                }
            }
        } else {
            prompt.append("No tools are currently available.\n\n");
        }

        // 4. 代码输出格式指令
        prompt.append(CODE_OUTPUT_FORMAT_INSTRUCTION).append("\n\n");

        // 5. 通用规则
        prompt.append(GENERAL_RULES).append("\n\n");

        // 6. 当前时间
        prompt.append("Current Date and Time: ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
              .append("\n\n");

        // 7. 用户自定义指令
        if (customInstructions != null && !customInstructions.trim().isEmpty()) {
            prompt.append("## Custom Instructions\n");
            prompt.append(customInstructions).append("\n\n");
        }
        
        // 可以考虑在这里添加代码上下文，但通常上下文是通过消息历史传递的
        // if (codeContext != null && !codeContext.trim().isEmpty()) {
        //     prompt.append("## Current Code Context\n```\n");
        //     prompt.append(codeContext).append("\n```\n\n");
        // }

        prompt.append("You may begin.");

        return prompt.toString();
    }
} 