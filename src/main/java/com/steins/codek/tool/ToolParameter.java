package com.steins.codek.tool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述一个工具的参数。
 * @author 0027013824
 */
public class ToolParameter {
    private final String name;
    private final String type;
    private final String description;
    private final boolean required;

    /**
     * 构造函数 (3个参数，用于兼容之前的 ReadFileTool 初始化)。
     * Type 默认为 "string"。
     * @param name 参数名称 (例如 "filePath", "startLine")。
     * @param description 参数描述。
     * @param required 此参数是否必需。
     */
    public ToolParameter(@NotNull String name, @NotNull String description, boolean required) {
        this(name, "string", description, required);
    }
    
    /**
     * 完整的构造函数。
     * @param name 参数名称。
     * @param type 参数类型 (例如 "string", "integer", "boolean")。
     * @param description 参数描述。
     * @param required 此参数是否必需。
     */
     public ToolParameter(@NotNull String name, @NotNull String type, @NotNull String description, boolean required) {
         this.name = name;
         this.type = type;
         this.description = description;
         this.required = required;
     }

    @NotNull
    public String getName() {
        return name;
    }
    
    /**
     * 获取参数类型。
     * @return 类型字符串 (例如 "string", "integer")。
     */
    @NotNull
    public String getType() {
         return type;
    }

    @NotNull
    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }
} 