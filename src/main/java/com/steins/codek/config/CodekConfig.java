package com.steins.codek.config;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CodeK插件的配置管理类，负责处理API密钥、API地址和模型选择等配置。
 * @author 0027013824
 */
public class CodekConfig {
    private static final Logger LOG = Logger.getInstance(CodekConfig.class);
    // 使用统一前缀，避免与其他插件冲突
    private static final String SERVICE_PREFIX = "com.steins.codek.";
    private static final String API_KEY_ATTR = SERVICE_PREFIX + "api_key";
    private static final String API_URL_PROPERTY = SERVICE_PREFIX + "api_url";
    private static final String MODEL_PROPERTY = SERVICE_PREFIX + "model";
    private static final String SUBSYSTEM = "CodeKAssistant"; // 用于CredentialAttributes

    // 默认值
    public static final String DEFAULT_MODEL = "claude-3.7-sonnet";
    public static final String DEFAULT_API_URL = "https://dev.iwhalecloud.com/faas/serverless/gpt-api-gw/v1/chat/completions";

    // 可用模型列表 (仅作建议，用户可输入任意值)
    public static final String[] SUGGESTED_MODELS = {
            "claude-3.5-sonnet",
            "claude-3.7-sonnet",
            "claude-3.7-sonnet-thinking",
            "DeepSeek-R1",
            "DeepSeek-V3",
            "gemini-2.0-flash",
            "gemini-2.0-flash-thinking",
            "gpt-4-omni",
            "gpt-4-turbo",
            "gpt-4o-mini",
            "gpt-3.5-turbo",
            "custom_model"
    };

    private final PropertiesComponent properties;
    // 缓存的API密钥，避免频繁访问PasswordSafe
    private volatile String cachedApiKey;

    /**
     * 构造函数。
     */
    public CodekConfig() {
        // 获取应用级的PropertiesComponent实例
        this.properties = PropertiesComponent.getInstance();
        // 预加载API密钥
        preloadApiKey();
    }

    public String getCustomInstructions() {
        return "";
    }

    /**
     * 在后台线程中预加载API密钥。
     */
    private void preloadApiKey() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                cachedApiKey = doGetApiKey();
            } 
            catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("预加载API密钥时出错: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 创建用于API密钥的CredentialAttributes对象。
     * @return CredentialAttributes对象。
     */
    private CredentialAttributes createCredentialAttributes() {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SUBSYSTEM, API_KEY_ATTR)
        );
    }

    /**
     * 在非EDT线程中执行获取密码的操作。
     * @return API密钥。
     */
    private String doGetApiKey() {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> 
            PasswordSafe.getInstance().getPassword(createCredentialAttributes())
        );
    }

    /**
     * 获取保存的API密钥。
     * 首先尝试返回缓存的值，如果没有则在后台线程中获取。
     * @return API密钥，如果没有保存则返回null。
     */
    public String getApiKey() {
        // 如果已经缓存了API密钥，直接返回
        if (cachedApiKey != null) {
            return cachedApiKey;
        }

        try {
            // 在后台线程中执行密码获取操作
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    this::doGetApiKey,
                    AppExecutorUtil.getAppExecutorService()
            );
            
            // 设置超时，避免长时间阻塞
            cachedApiKey = future.get(500, TimeUnit.MILLISECONDS);
            return cachedApiKey;
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("获取API密钥时出错: " + e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * 保存API密钥。
     * 更新缓存并在后台线程中保存。
     * @param apiKey 要保存的API密钥。
     */
    public void setApiKey(String apiKey) {
        // 立即更新缓存
        this.cachedApiKey = apiKey;
        
        // 在后台线程中保存密钥
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ApplicationManager.getApplication().runWriteAction((Computable<Void>) () -> {
                    PasswordSafe.getInstance().setPassword(createCredentialAttributes(), apiKey);
                    return null;
                });
            }
            catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("保存API密钥时出错: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 获取配置的API地址。
     * @return API地址，如果没有配置则返回默认地址。
     */
    public String getApiUrl() {
        return properties.getValue(API_URL_PROPERTY, DEFAULT_API_URL);
    }

    /**
     * 设置API地址。
     * @param apiUrl API地址。
     */
    public void setApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            properties.setValue(API_URL_PROPERTY, DEFAULT_API_URL);
        } else {
            // 简单验证URL格式 (可选)
            // if (!apiUrl.trim().matches("^https?://.*")) { ... }
            properties.setValue(API_URL_PROPERTY, apiUrl.trim());
        }
    }

    /**
     * 获取当前选择的模型。
     * @return 当前模型名称。
     */
    public String getCurrentModel() {
        return properties.getValue(MODEL_PROPERTY, DEFAULT_MODEL);
    }

    /**
     * 设置当前选择的模型。
     * @param model 模型名称。
     */
    public void setCurrentModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            properties.setValue(MODEL_PROPERTY, DEFAULT_MODEL);
        } else {
            properties.setValue(MODEL_PROPERTY, model.trim());
        }
    }

    /**
     * 获取建议的模型列表。
     * @return 模型名称数组。
     */
    public String[] getSuggestedModels() {
        return SUGGESTED_MODELS;
    }
}