package org.wso2.carbon.apimgt.gateway.mediators;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.AbstractMediator;
import org.wso2.carbon.apimgt.api.APIConstants;
import org.wso2.carbon.apimgt.gateway.mediators.Json_requests.LLMTemplateFactory;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.carbon.apimgt.gateway.internal.DataHolder;
import org.wso2.carbon.apimgt.gateway.utils.GatewayUtils;
import org.wso2.carbon.apimgt.api.gateway.ModelEndpointDTO;
import org.wso2.carbon.apimgt.api.gateway.RequestChangePolicyConfigDTO;
import org.wso2.carbon.apimgt.api.model.LLMProviderInfo;
import org.wso2.carbon.apimgt.api.LLMProviderConfiguration;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Simple DTO to hold user request input containing message content
 */
class UserRequestInput {
    private String message;
    private String model; // Optional, will be ignored as we use target model from config
    
    public UserRequestInput() {}
    
    public UserRequestInput(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
}

/**
 * RequestChangeMediator intercepts AI API requests and routes them to a different LLM provider.
 * It extracts message content from the original request, converts it to the target LLM format,
 * sets the target endpoint, and modifies the request payload for the new provider.
 * This allows changing the LLM model/provider without modifying the client request.
 * 
 * Works like LLMRouteMediator and RoundRobinMediator with production/sandbox endpoints.
 * Only requires model name and endpoint ID - other details come from API Manager internally.
 */
public class RequestChangeMediator extends AbstractMediator implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(RequestChangeMediator.class);
    private String requestChangeConfigs; // JSON config with production/sandbox endpoints
    
    static {
        System.out.println("RequestChangeMediator class loaded!");
        log.info("RequestChangeMediator class loaded!");
    }

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {
        if (log.isDebugEnabled()) {
            log.debug("RequestChangeMediator: Initialized.");
            log.debug("RequestChangeMediator: Configuration = " + requestChangeConfigs);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean mediate(MessageContext messageContext) {
        log.info("RequestChangeMediator: Mediate method called - START");
        if (log.isDebugEnabled()) {
            log.debug("RequestChangeMediator: Request routing mediation started.");
            log.debug("RequestChangeMediator: Configuration = " + requestChangeConfigs);
        }

        try {
            DataHolder.getInstance().initCache(GatewayUtils.getAPIKeyForEndpoints(messageContext));

            if (requestChangeConfigs == null || requestChangeConfigs.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("No request change configuration provided, bypassing mediator");
                }
                return true;
            }

            // Parse configuration like other mediators
            RequestChangePolicyConfigDTO config;
            try {
                config = new Gson().fromJson(requestChangeConfigs, RequestChangePolicyConfigDTO.class);
                if (config == null) {
                    log.error("Failed to parse request change configuration: null config");
                    return false;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Parsed request change config - Production: " + 
                        (config.getProduction() != null ? config.getProduction().getEndpointId() : "null") +
                        ", Sandbox: " + 
                        (config.getSandbox() != null ? config.getSandbox().getEndpointId() : "null"));
                }
            } catch (JsonSyntaxException e) {
                log.error("Failed to parse request change configuration", e);
                return false;
            }

            // Validate configuration
            if (!config.isValid()) {
                log.error("Invalid request change configuration");
                return false;
            }

            // Get target endpoint based on environment (production/sandbox) like other mediators
            String environment = getEnvironment(messageContext);
            ModelEndpointDTO targetEndpoint = getTargetEndpoint(messageContext, config);
            
            if (targetEndpoint == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No target endpoint found for environment: " + environment + ", rejecting request");
                }
                messageContext.setProperty(APIConstants.AIAPIConstants.TARGET_ENDPOINT, APIConstants.AIAPIConstants.REJECT_ENDPOINT);
                return true;
            }

            if (log.isDebugEnabled()) {
                log.debug("Selected target endpoint: " + targetEndpoint.getEndpointId() + 
                         " with model: " + targetEndpoint.getModel() + 
                         " for environment: " + environment);
            }

            // Get provider info from API Manager using endpoint ID
            LLMProviderInfo targetProvider = getProviderFromEndpointId(targetEndpoint.getEndpointId());
            if (targetProvider == null) {
                log.error("No provider found for endpoint ID: " + targetEndpoint.getEndpointId());
                return false;
            }

            // Extract user input - expect JSON with model name only
            UserRequestInput userInput = extractUserRequestInput(messageContext);
            if (userInput == null || userInput.getMessage() == null || userInput.getMessage().trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("No user message content found in request, bypassing request change");
                }
                return true;
            }

            // Use target model from configuration, not from user input
            String targetModel = targetEndpoint.getModel();
            String userMessage = userInput.getMessage();
            
            if (log.isDebugEnabled()) {
                log.debug("Processing user message for target model: " + targetModel + ", provider: " + targetProvider.getName());
            }

            // Create new request for target model with hardcoded template values (as specified)
            String jsonRequest = LLMTemplateFactory.createRequest(
                targetProvider.getName().toLowerCase(), 
                userMessage, 
                targetModel,
                1000, // Hardcoded max tokens
                0.7   // Hardcoded temperature
            );
            
            if (jsonRequest == null || jsonRequest.trim().isEmpty()) {
                log.error("Failed to create JSON request for provider: " + targetProvider.getName());
                return false;
            }

            // Set up endpoint configuration for AIAPIMediator to use
            setupEndpointForAIAPIMediator(messageContext, targetEndpoint, targetProvider);
            
            // Set appropriate headers for the target LLM
            setTargetHeaders(messageContext, targetEndpoint, targetProvider);
            
            // Replace the request payload with the new JSON request
            replaceRequestPayload(messageContext, jsonRequest);

            // Set configuration for AIAPIMediator integration
            setupAIAPIMediatorIntegration(messageContext, targetEndpoint, targetProvider);

            if (log.isDebugEnabled()) {
                log.debug("Successfully set target endpoint and modified request for: " + targetProvider.getName());
            }

            if (log.isDebugEnabled()) {
                log.debug("RequestChangeMediator: Request successfully routed to " + targetProvider.getName());
            }

        } catch (Exception e) {
            log.error("Error during request change mediation", e);
            return false;
        }

        return true;
    }

    private String getEnvironment(MessageContext messageContext) {
        String apiKeyType = (String) messageContext.getProperty(org.wso2.carbon.apimgt.impl.APIConstants.API_KEY_TYPE);
        return org.wso2.carbon.apimgt.impl.APIConstants.API_KEY_TYPE_PRODUCTION.equals(apiKeyType) ? "production" : "sandbox";
    }

    private ModelEndpointDTO getTargetEndpoint(MessageContext messageContext, RequestChangePolicyConfigDTO config) {
        String apiKeyType = (String) messageContext.getProperty(org.wso2.carbon.apimgt.impl.APIConstants.API_KEY_TYPE);
        ModelEndpointDTO targetEndpoint = org.wso2.carbon.apimgt.impl.APIConstants.API_KEY_TYPE_PRODUCTION.equals(apiKeyType)
                ? config.getProduction()
                : config.getSandbox();

        if (targetEndpoint == null) {
            if (log.isDebugEnabled()) {
                log.debug("Request change policy is not set for " + apiKeyType);
            }
            return null;
        }

        // Check if endpoint is active (like other mediators)
        if (DataHolder.getInstance().isEndpointSuspended(GatewayUtils.getAPIKeyForEndpoints(messageContext), 
                GatewayUtils.getEndpointKey(targetEndpoint))) {
            if (log.isDebugEnabled()) {
                log.debug("Target endpoint " + targetEndpoint.getEndpointId() + " is suspended");
            }
            return null;
        }

        return targetEndpoint;
    }

    private LLMProviderInfo getProviderFromEndpointId(String endpointId) {
        // This would typically get provider info from API Manager using endpoint ID
        // For now, we'll use a simple mapping or get from DataHolder
        // In real implementation, this would query the endpoint registry
        
        // Try to get from DataHolder first
        LLMProviderInfo provider = DataHolder.getInstance().getLLMProviderConfigurations(endpointId);
        if (provider != null) {
            return provider;
        }
        
        // Fallback: try to extract provider from endpoint ID pattern
        if (endpointId.contains("claude")) {
            return createClaudeProvider();
        } else if (endpointId.contains("openai")) {
            return createOpenAIProvider();
        } else if (endpointId.contains("gemini")) {
            return createGeminiProvider();
        } else if (endpointId.contains("mistral")) {
            return createMistralProvider();
        }
        
        // Default to OpenAI
        return createOpenAIProvider();
    }

    private LLMProviderInfo createClaudeProvider() {
        LLMProviderInfo provider = new LLMProviderInfo();
        provider.setId("claude-provider");
        provider.setName("claude");
        provider.setApiVersion("1.0.0");
        return provider;
    }

    private LLMProviderInfo createOpenAIProvider() {
        LLMProviderInfo provider = new LLMProviderInfo();
        provider.setId("openai-provider");
        provider.setName("openai");
        provider.setApiVersion("1.0.0");
        return provider;
    }

    private LLMProviderInfo createGeminiProvider() {
        LLMProviderInfo provider = new LLMProviderInfo();
        provider.setId("gemini-provider");
        provider.setName("gemini");
        provider.setApiVersion("1.0.0");
        return provider;
    }

    private LLMProviderInfo createMistralProvider() {
        LLMProviderInfo provider = new LLMProviderInfo();
        provider.setId("mistral-provider");
        provider.setName("mistral");
        provider.setApiVersion("1.0.0");
        return provider;
    }

    private UserRequestInput extractUserRequestInput(MessageContext messageContext) {
        try {
            org.apache.axis2.context.MessageContext axis2MessageContext = 
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            
            RelayUtils.buildMessage(axis2MessageContext);
            
            if (JsonUtil.hasAJsonPayload(axis2MessageContext)) {
                String payload = JsonUtil.jsonPayloadToString(axis2MessageContext);
                return parseUserRequestFromJson(payload);
            } else if (messageContext.getEnvelope().getBody() != null) {
                Object objFirstElement = messageContext.getEnvelope().getBody().getFirstElement();
                if (objFirstElement != null) {
                    String payload = objFirstElement.toString();
                    return parseUserRequestFromJson(payload);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting user request input", e);
        }
        return null;
    }
    
    private UserRequestInput parseUserRequestFromJson(String payload) {
        try {
            if (payload == null || payload.trim().isEmpty()) {
                return null;
            }
            
            // First try to parse as direct UserRequestInput JSON
            try {
                Gson gson = new Gson();
                UserRequestInput userInput = gson.fromJson(payload, UserRequestInput.class);
                if (userInput != null && userInput.getMessage() != null && !userInput.getMessage().trim().isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Parsed user input directly from JSON");
                    }
                    return userInput;
                }
            } catch (JsonSyntaxException e) {
                // Not a direct UserRequestInput format, try extracting from various formats
            }
            
            // Try to extract message from various common LLM request formats
            String extractedMessage = extractMessageFromPayload(payload);
            if (extractedMessage != null && !extractedMessage.trim().isEmpty()) {
                UserRequestInput userInput = new UserRequestInput(extractedMessage);
                if (log.isDebugEnabled()) {
                    log.debug("Extracted message from payload format");
                }
                return userInput;
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error parsing user request from JSON", e);
            return null;
        }
    }

    private String extractMessageFromPayload(String payload) {
        try {
            if (payload.contains("messages")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.messages[0].content", String.class);
                } catch (PathNotFoundException e) {
                    try {
                        return context.read("$.messages[0].text", String.class);
                    } catch (PathNotFoundException e2) {
                        return null;
                    }
                }
            } else if (payload.contains("prompt")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.prompt", String.class);
                } catch (PathNotFoundException e) {
                    return null;
                }
            } else if (payload.contains("input")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.input", String.class);
                } catch (PathNotFoundException e) {
                    return null;
                }
            } else if (payload.contains("text")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.text", String.class);
                } catch (PathNotFoundException e) {
                    return null;
                }
            } else if (payload.contains("query")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.query", String.class);
                } catch (PathNotFoundException e) {
                    return null;
                }
            } else if (payload.contains("question")) {
                DocumentContext context = JsonPath.parse(payload);
                try {
                    return context.read("$.question", String.class);
                } catch (PathNotFoundException e) {
                    return null;
                }
            }
            return payload;
        } catch (Exception e) {
            log.error("Error extracting message content", e);
            return payload;
        }
    }

    private void setupEndpointForAIAPIMediator(MessageContext messageContext, ModelEndpointDTO targetEndpoint, LLMProviderInfo targetProvider) {
        try {
            // Get endpoint URL from API Manager using endpoint ID
            String endpointUrl = getEndpointUrlFromAPIManager(targetEndpoint.getEndpointId());
            if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
                log.error("No endpoint URL found for endpoint ID: " + targetEndpoint.getEndpointId());
                return;
            }
            
            // Set the target endpoint ID that AIAPIMediator will use
            messageContext.setProperty(APIConstants.AIAPIConstants.TARGET_ENDPOINT, targetEndpoint.getEndpointId());
            
            // Set endpoint address properties for routing
            org.apache.axis2.context.MessageContext axis2MessageContext = 
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            axis2MessageContext.setProperty("ENDPOINT_ADDRESS", endpointUrl);
            axis2MessageContext.getOptions().setTo(new org.apache.axis2.addressing.EndpointReference(endpointUrl));
            
            if (log.isDebugEnabled()) {
                log.debug("Set up endpoint configuration for AIAPIMediator: " + targetEndpoint.getEndpointId() + " -> " + endpointUrl);
            }
        } catch (Exception e) {
            log.error("Error setting up endpoint for AIAPIMediator", e);
        }
    }

    private String getEndpointUrlFromAPIManager(String endpointId) {
        // Get endpoint URL from API Manager's endpoint registry
        try {
            // First try to get from DataHolder cache or registry
            String cachedUrl = DataHolder.getInstance().getEndpointUrl(endpointId);
            if (cachedUrl != null && !cachedUrl.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrieved cached endpoint URL for ID: " + endpointId + " -> " + cachedUrl);
                }
                return cachedUrl;
            }
            
            // Try to get from API Manager's endpoint registry using APIUtil
            // This would be the proper way to get endpoint URL from WSO2 APIM
            try {
                // TODO: Implement proper APIUtil.getEndpointUrlFromRegistry(endpointId) when available
                String registryUrl = null; // APIUtil.getEndpointUrlFromRegistry(endpointId);
                if (registryUrl != null && !registryUrl.trim().isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Retrieved endpoint URL from registry for ID: " + endpointId + " -> " + registryUrl);
                    }
                    return registryUrl;
                }
            } catch (Exception e) {
                log.warn("Failed to get endpoint URL from registry for ID: " + endpointId + ", using fallback: " + e.getMessage());
            }
            
            // FALLBACK: Use pattern-based URL mapping for known providers
            // This ensures compatibility while proper registry integration is being implemented
            log.debug("Using pattern-based endpoint URL mapping for endpoint ID: " + endpointId);
            
            String providerType = extractProviderTypeFromEndpointId(endpointId);
            switch (providerType.toLowerCase()) {
                case "claude":
                case "anthropic":
                    return "https://api.anthropic.com/v1/messages";
                case "openai":
                    return "https://api.openai.com/v1/chat/completions";
                case "gemini":
                case "google":
                    return "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
                case "mistral":
                    return "https://api.mistral.ai/v1/chat/completions";
                case "azure":
                case "azureopenai":
                    return "https://your-azure-openai.openai.azure.com/openai/deployments/gpt-4/chat/completions?api-version=2023-05-15";
                case "bedrock":
                case "aws":
                    return "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/invoke";
                default:
                    log.warn("Unknown provider type: " + providerType + " for endpoint ID: " + endpointId + ", using default OpenAI URL");
                    return "https://api.openai.com/v1/chat/completions";
            }
        } catch (Exception e) {
            log.error("Error getting endpoint URL from API Manager for endpoint ID: " + endpointId, e);
            return null;
        }
    }
    
    private String extractProviderTypeFromEndpointId(String endpointId) {
        // Extract provider type from endpoint ID
        if (endpointId == null || endpointId.trim().isEmpty()) {
            return "openai"; // default
        }
        
        String lowerEndpointId = endpointId.toLowerCase();
        
        if (lowerEndpointId.contains("claude") || lowerEndpointId.contains("anthropic")) {
            return "claude";
        } else if (lowerEndpointId.contains("openai") && !lowerEndpointId.contains("azure")) {
            return "openai";
        } else if (lowerEndpointId.contains("gemini") || lowerEndpointId.contains("google")) {
            return "gemini";
        } else if (lowerEndpointId.contains("mistral")) {
            return "mistral";
        } else if (lowerEndpointId.contains("azure")) {
            return "azure";
        } else if (lowerEndpointId.contains("bedrock") || lowerEndpointId.contains("aws")) {
            return "bedrock";
        } else {
            return "openai"; // default fallback
        }
    }
    
    private void setTargetHeaders(MessageContext messageContext, ModelEndpointDTO targetEndpoint, LLMProviderInfo targetProvider) {
        try {
            org.apache.axis2.context.MessageContext axis2MessageContext = 
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            
            // Get transport headers
            java.util.Map<String, Object> headers = 
                (java.util.Map<String, Object>) axis2MessageContext.getProperty(
                    org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
            
            if (headers == null) {
                headers = new java.util.HashMap<>();
                axis2MessageContext.setProperty(
                    org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headers);
            }
            
            // Set Content-Type for all providers
            headers.put("Content-Type", "application/json");
            
            // Get API key from API Manager
            String apiKey = getApiKeyFromAPIManager(messageContext, targetEndpoint.getEndpointId());
            
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                String providerName = targetProvider.getName().toLowerCase();
                
                // Set provider-specific authentication headers based on WSO2 APIM configuration
                setProviderSpecificHeaders(headers, providerName, apiKey);
                
                if (log.isDebugEnabled()) {
                    log.debug("Set authentication headers for provider: " + providerName);
                }
            } else {
                log.warn("No API key found for endpoint: " + targetEndpoint.getEndpointId() + " - authentication headers not set");
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Successfully set headers for target LLM: " + targetProvider.getName());
            }
        } catch (Exception e) {
            log.error("Error setting target headers", e);
        }
    }
    
    private void setProviderSpecificHeaders(java.util.Map<String, Object> headers, String providerName, String apiKey) {
        // Set provider-specific authentication headers according to each provider's requirements
        switch (providerName) {
            case "openai":
                // OpenAI API: Authorization: Bearer sk-xxx
                headers.put("Authorization", "Bearer " + apiKey);
                break;
                
            case "claude":
            case "anthropic":
                // Claude/Anthropic API: x-api-key + anthropic-version header
                headers.put("x-api-key", apiKey);
                headers.put("anthropic-version", "2023-06-01");
                break;
                
            case "mistral":
            case "mistralai":
                // Mistral AI: Authorization: Bearer xxx
                headers.put("Authorization", "Bearer " + apiKey);
                break;
                
            case "gemini":
            case "google":
                // Google Gemini: Can use either Authorization Bearer or x-goog-api-key
                if (apiKey.startsWith("AIza")) {
                    // Google API key format
                    headers.put("x-goog-api-key", apiKey);
                } else {
                    // OAuth token format
                    headers.put("Authorization", "Bearer " + apiKey);
                }
                break;
                
            case "azure":
            case "azureopenai":
                // Azure OpenAI: api-key header
                headers.put("api-key", apiKey);
                break;
                
            case "aws":
            case "bedrock":
            case "awsbedrock":
                // AWS Bedrock: Uses AWS Signature V4 authentication (complex)
                // For now, set basic Authorization header - proper AWS Sig V4 implementation needed
                headers.put("Authorization", "AWS4-HMAC-SHA256 " + apiKey);
                headers.put("Content-Type", "application/json");
                break;
                
            case "huggingface":
            case "hf":
                // Hugging Face: Authorization: Bearer hf_xxx
                headers.put("Authorization", "Bearer " + apiKey);
                break;
                
            case "cohere":
                // Cohere: Authorization: Bearer xxx
                headers.put("Authorization", "Bearer " + apiKey);
                break;
                
            case "palmapi":
            case "palm":
                // Google PaLM API: x-goog-api-key
                headers.put("x-goog-api-key", apiKey);
                break;
                
            case "openrouter":
                // OpenRouter: Authorization: Bearer xxx + HTTP-Referer
                headers.put("Authorization", "Bearer " + apiKey);
                headers.put("HTTP-Referer", "https://wso2.com");
                break;
                
            default:
                // Default to Bearer token authentication for unknown providers
                headers.put("Authorization", "Bearer " + apiKey);
                if (log.isDebugEnabled()) {
                    log.debug("Unknown provider: " + providerName + ", using default Bearer token authentication");
                }
                break;
        }
    }
    
    private String getApiKeyFromAPIManager(MessageContext messageContext, String endpointId) {
        // Get API key from API Manager's secure storage
        try {
            // First try to get from DataHolder cache - this would contain keys from APIM
            String cachedApiKey = DataHolder.getInstance().getApiKeyForEndpoint(endpointId);
            if (cachedApiKey != null && !cachedApiKey.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrieved cached API key for endpoint: " + endpointId);
                }
                return cachedApiKey;
            }
            
            // Try to get from WSO2 APIM secure storage via APIUtil
            try {
                // TODO: Implement proper APIUtil.getApiKeyFromSecureStorage(endpointId, messageContext) when available
                String secureApiKey = null; // APIUtil.getApiKeyFromSecureStorage(endpointId, messageContext);
                if (secureApiKey != null && !secureApiKey.trim().isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Retrieved API key from secure storage for endpoint: " + endpointId);
                    }
                    return secureApiKey;
                }
            } catch (Exception e) {
                log.warn("Failed to get API key from secure storage for endpoint: " + endpointId + ", trying fallback: " + e.getMessage());
            }
            
            // Try to get from message context properties (configured via API Manager)
            String endpointApiKey = (String) messageContext.getProperty("ENDPOINT_API_KEY_" + endpointId);
            if (endpointApiKey != null && !endpointApiKey.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrieved endpoint-specific API key from message context for: " + endpointId);
                }
                return endpointApiKey;
            }
            
            // Try provider-specific API key
            String providerType = extractProviderTypeFromEndpointId(endpointId);
            String providerApiKey = (String) messageContext.getProperty("API_KEY_" + providerType.toUpperCase());
            if (providerApiKey != null && !providerApiKey.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrieved provider-specific API key for: " + providerType);
                }
                return providerApiKey;
            }
            
            // Try generic API key as last fallback
            String genericApiKey = (String) messageContext.getProperty("API_KEY");
            if (genericApiKey != null && !genericApiKey.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Using generic API key for endpoint: " + endpointId);
                }
                return genericApiKey;
            }
            
            log.warn("No API key found for endpoint: " + endpointId + " - ensure proper configuration in WSO2 API Manager");
            return null;
        } catch (Exception e) {
            log.error("Error getting API key from API Manager for endpoint ID: " + endpointId, e);
            return null;
        }
    }
    
    private void replaceRequestPayload(MessageContext messageContext, String newPayload) throws IOException, XMLStreamException {
        try {
            org.apache.axis2.context.MessageContext axis2MessageContext = 
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            
            RelayUtils.buildMessage(axis2MessageContext);
            
            JsonUtil.getNewJsonPayload(axis2MessageContext, newPayload, true, true);
            
            if (log.isDebugEnabled()) {
                log.debug("Successfully replaced request payload with new LLM request");
            }
        } catch (Exception e) {
            log.error("Error replacing request payload", e);
            throw e;
        }
    }

    public String getRequestChangeConfigs() {
        return requestChangeConfigs;
    }

    public void setRequestChangeConfigs(String requestChangeConfigs) {
        this.requestChangeConfigs = requestChangeConfigs;
    }
    
    private void setupAIAPIMediatorIntegration(MessageContext messageContext, ModelEndpointDTO targetEndpoint, LLMProviderInfo targetProvider) {
        try {
            // Set configuration for AIAPIMediator to recognize RequestChangeMediator usage
            messageContext.setProperty(APIConstants.AIAPIConstants.REQUEST_CHANGE_CONFIGS, requestChangeConfigs);
            
            // Set provider information for AIAPIMediator
            messageContext.setProperty(APIConstants.AIAPIConstants.LLM_PROVIDER_ID, targetProvider.getId());
            messageContext.setProperty(APIConstants.AIAPIConstants.LLM_PROVIDER_NAME, targetProvider.getName());
            
            // Set model information
            messageContext.setProperty(APIConstants.AIAPIConstants.TARGET_MODEL, targetEndpoint.getModel());
            messageContext.setProperty(APIConstants.AIAPIConstants.TARGET_MODEL_ENDPOINT, targetEndpoint);
            
            // Mark that request has been changed by RequestChangeMediator
            messageContext.setProperty(APIConstants.AIAPIConstants.REQUEST_CHANGED_BY_MEDIATOR, "true");
            
            // Set timeout for the target endpoint
            try {
                messageContext.setProperty(APIConstants.AIAPIConstants.REQUEST_TIMEOUT,
                        APIUtil.getDefaultRequestTimeoutsForAIAPIs() * APIConstants.AIAPIConstants.MILLISECONDS_IN_SECOND);
            } catch (Exception e) {
                log.warn("Failed to set request timeout, using default: " + e.getMessage());
                messageContext.setProperty(APIConstants.AIAPIConstants.REQUEST_TIMEOUT, 30000); // 30 seconds default
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Set up AIAPIMediator integration properties for target: " + targetEndpoint.getEndpointId());
            }
        } catch (Exception e) {
            log.error("Error setting up AIAPIMediator integration", e);
        }
    }
}
