package com.steins.codek.tool;

import com.steins.codek.tool.ToolParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 代表一个可被 LLM 调用的工具。
 * @author 0027013824
 */
public interface Tool {

    /**
     * 获取工具的唯一名称。
     * LLM 将使用此名称来调用工具。
     * @return 工具名称 (例如 "read_file")。
     */
    @NotNull
    String getName();

    /**
     * 获取工具的描述。
     * 这段描述将包含在系统提示词中，告知 LLM 工具的功能、使用场景和参数。
     * @return 工具描述。
     */
    @NotNull
    String getDescription();

    /**
     * 获取工具所需的参数列表。
     * @return 参数描述列表。
     */
    @NotNull
    List<ToolParameter> getParameters();

    /**
     * 执行工具。
     *
     * @param arguments 从 LLM 调用中解析出的参数键值对。
     * @return 工具执行的结果字符串，将返回给 LLM。
     * @throws Exception 如果执行过程中发生错误。
     */
    @NotNull
    String execute(@NotNull Map<String, String> arguments) throws Exception;

    /**
     * (可选) 指定此工具执行前是否需要用户明确批准。
     * 默认为 false。对于修改文件、执行命令等敏感操作应返回 true。
     * @return 是否需要用户批准。
     */
    default boolean requiresApproval() {
        return false;
    }
} 