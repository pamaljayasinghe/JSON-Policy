package org.wso2.carbon.apimgt.gateway.mediators;

public class RequestChangeConfigDTO {
    
    private String apiKey;
    private String model;
    private String endpoint;
    
    // Smart defaults for backward compatibility
    private String providerName;
    private String authType;
    private Integer timeout;
    private Integer maxTokens;
    private Double temperature;
    
    public RequestChangeConfigDTO() {
        // Set smart defaults
        this.authType = "bearer"; // Default to bearer token
        this.timeout = 30000; // Default 30 seconds
        this.maxTokens = 1000; // Default max tokens
        this.temperature = 0.7; // Default temperature
    }
    
    public RequestChangeConfigDTO(String apiKey, String model, String endpoint) {
        this();
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
        // Auto-detect provider from model name
        this.providerName = detectProviderFromModel(model);
    }
    
    // Auto-detect provider from model name
    private String detectProviderFromModel(String model) {
        if (model == null) return "openai";
        
        String modelLower = model.toLowerCase();
        
        // Check for Azure-specific models first
        if (modelLower.contains("gpt-4o") || modelLower.contains("gpt-4-turbo")) {
            // These are newer models that could be Azure or OpenAI
            // We'll determine based on endpoint URL
            return detectProviderFromEndpoint();
        }
        
        if (modelLower.contains("gpt") || modelLower.contains("openai")) {
            // For older models, check if endpoint suggests Azure
            if (isAzureEndpoint()) {
                return "azureopenai";
            }
            return "openai";
        } else if (modelLower.contains("claude")) {
            return "claude";
        } else if (modelLower.contains("gemini")) {
            return "gemini";
        } else if (modelLower.contains("mistral")) {
            return "mistral";
        } else if (modelLower.contains("bedrock") || modelLower.contains("aws")) {
            return "awsbedrock";
        }
        return "openai"; // Default fallback
    }
    
    // Detect if endpoint is Azure OpenAI
    private boolean isAzureEndpoint() {
        if (endpoint == null) return false;
        return endpoint.toLowerCase().contains("azure.com") || 
               endpoint.toLowerCase().contains("openai.azure.com");
    }
    
    // Detect provider from endpoint URL (more reliable)
    private String detectProviderFromEndpoint() {
        if (endpoint == null) return "openai";
        
        String endpointLower = endpoint.toLowerCase();
        if (endpointLower.contains("azure.com") || endpointLower.contains("openai.azure.com")) {
            return "azureopenai";
        } else if (endpointLower.contains("anthropic.com")) {
            return "claude";
        } else if (endpointLower.contains("generativelanguage.googleapis.com")) {
            return "gemini";
        } else if (endpointLower.contains("mistral.ai")) {
            return "mistral";
        } else if (endpointLower.contains("bedrock") || endpointLower.contains("amazonaws.com")) {
            return "awsbedrock";
        } else if (endpointLower.contains("openai.com")) {
            return "openai";
        }
        return "openai"; // Default fallback
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
        this.providerName = detectProviderFromModel(model);
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    // Getters for backward compatibility with smart defaults
    public String getProviderName() {
        if (providerName != null) {
            return providerName;
        }
        // Use endpoint-based detection for more accuracy
        return detectProviderFromEndpoint();
    }
    
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    
    public String getAuthType() {
        return authType != null ? authType : "bearer";
    }
    
    public void setAuthType(String authType) {
        this.authType = authType;
    }
    
    public Integer getTimeout() {
        return timeout != null ? timeout : 30000;
    }
    
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
    
    public Integer getMaxTokens() {
        return maxTokens != null ? maxTokens : 1000;
    }
    
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public Double getTemperature() {
        return temperature != null ? temperature : 0.7;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}
