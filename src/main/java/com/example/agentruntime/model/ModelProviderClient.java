package com.example.agentruntime.model;

/**
 * 模型供应商适配器接口。
 */
public interface ModelProviderClient {

    boolean supports(String providerType);

    ModelChatResponse chat(ModelRuntimeProfile profile, ModelChatRequest request);
}
