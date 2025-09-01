package org.wso2.carbon.apimgt.gateway.mediators;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.carbon.apimgt.api.APIConstants.AIAPIConstants;
import org.wso2.carbon.apimgt.api.gateway.ModelEndpointDTO;
import org.wso2.carbon.apimgt.api.gateway.LLMRPolicyConfigDTO;
import org.wso2.carbon.apimgt.gateway.internal.DataHolder;
import org.wso2.carbon.apimgt.gateway.utils.GatewayUtils;
import org.wso2.carbon.apimgt.gateway.mediators.MistralService;
import org.wso2.carbon.apimgt.impl.APIConstants;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import javax.xml.namespace.QName;


public class LLMRouteMediator extends AbstractMediator implements ManagedLifecycle {
    private static final Log log = LogFactory.getLog(LLMRouteMediator.class);

    private String llmRouteConfigs;

    public void setLlmRouteConfigs(String llmRouteConfigs) {
        this.llmRouteConfigs = llmRouteConfigs;
    }

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {
        if (log.isDebugEnabled()) {
            log.debug("LLMRouteMediator initialized.");
        }
    }

    @Override
    public boolean mediate(MessageContext messageContext) {
        if (log.isDebugEnabled()) {
            log.debug("LLMRouteMediator mediation started.");
        }

        try {
            DataHolder.getInstance().initCache(GatewayUtils.getAPIKeyForEndpoints(messageContext));

            if (llmRouteConfigs == null || llmRouteConfigs.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("No LLM routing configuration provided, bypassing mediator");
                }
                return true;
            }

            LLMRPolicyConfigDTO endpoints;
            try {
                endpoints = new Gson().fromJson(llmRouteConfigs, LLMRPolicyConfigDTO.class);
                if (endpoints == null) {
                    log.error("Failed to parse LLM routing configuration: null config");
                    return false;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Parsed LLM config - Production: " +
                            (endpoints.getProduction() != null ? "configured" : "null") +
                            ", Sandbox: " +
                            (endpoints.getSandbox() != null ? "configured" : "null"));
                    
                    if (endpoints.getProduction() != null) {
                        log.debug("Production defaultModel: " + 
                            (endpoints.getProduction().getDefaultModel() != null ? 
                                endpoints.getProduction().getDefaultModel().getModel() : "null"));
                        log.debug("Production categories: " + 
                            (endpoints.getProduction().getCategories() != null ? 
                                endpoints.getProduction().getCategories().keySet() : "null"));
                    }
                }
            } catch (JsonSyntaxException e) {
                log.error("Failed to parse LLM routing configuration", e);
                return false;
            }

            String environment = getEnvironment(messageContext);
            LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig = GatewayUtils.getLLMTargetConfig(messageContext, endpoints);
            
            if (log.isDebugEnabled()) {
                log.debug("Using environment: " + environment);
                log.debug("Target config: " + (targetConfig != null ? "found" : "null"));
                if (targetConfig != null) {
                    log.debug("Target config defaultModel: " + 
                        (targetConfig.getDefaultModel() != null ? 
                            targetConfig.getDefaultModel().getModel() : "null"));
                }
            }
            
            String classifiedCategory = classifyRequest(messageContext, targetConfig);

            if (log.isDebugEnabled()) {
                logAllAvailableEndpoints(targetConfig, classifiedCategory);
            }

            ModelEndpointDTO selectedEndpoint = GatewayUtils.selectLLMEndpoint(targetConfig, messageContext, classifiedCategory);
            if (selectedEndpoint != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Successfully selected endpoint: " + selectedEndpoint.getEndpointId() +
                            " for category: " + classifiedCategory);
                }
                messageContext.setProperty(AIAPIConstants.TARGET_ENDPOINT, selectedEndpoint.getEndpointId());

                Map<String, Object> llmRouteConfigs = new HashMap<>();
                llmRouteConfigs.put(AIAPIConstants.LLM_TARGET_MODEL_ENDPOINT, selectedEndpoint);
                llmRouteConfigs.put(AIAPIConstants.SUSPEND_DURATION,
                        endpoints.getSuspendDuration() * AIAPIConstants.MILLISECONDS_IN_SECOND);
                messageContext.setProperty(AIAPIConstants.LLM_ROUTE_CONFIGS, llmRouteConfigs);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No endpoint selected for category: " + classifiedCategory + ", rejecting request");
                }
                messageContext.setProperty(AIAPIConstants.TARGET_ENDPOINT, AIAPIConstants.REJECT_ENDPOINT);
            }

            return true;
        } catch (Exception e) {
            log.error("Error in LLMRouteMediator mediation", e);
            return false;
        }
    }

    private String extractUserRequestContent(MessageContext messageContext) {
        try {
            org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            RelayUtils.buildMessage(msgContext);
            SOAPEnvelope envelope = msgContext.getEnvelope();
            OMElement jsonObject = envelope.getBody().getFirstChildWithName(new QName("jsonObject"));
            OMElement messages = jsonObject.getFirstChildWithName(new QName("messages"));
            OMElement content = messages.getFirstChildWithName(new QName("content"));
            return content.getText();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error extracting user request content: " + e.getMessage());
            }
            return null;
        }
    }

    private String classifyRequest(MessageContext messageContext, LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        try {
            String content = extractUserRequestContent(messageContext);
            Set<String> availableCategories = getAvailableCategories(targetConfig);

            if (availableCategories == null || availableCategories.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("No categories available, will use default model");
                }
                return null;
            }

            if (content == null || content.trim().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("No content to classify, will use default model");
                }
                return null;
            }

            String categoryOptions = String.join(", ", availableCategories);
            String prompt = "Classify this request into one of these categories: " + categoryOptions +
                    ". Respond with exactly one category name from the list for this request: " + content;

            MistralService mistralService = new MistralService();

            if (!mistralService.isServiceAvailable()) {
                if (log.isDebugEnabled()) {
                    log.debug("Mistral service not available, will use default model");
                }
                return null;
            }

            String response = mistralService.classifyRequest(prompt);

            if (response != null) {
                String cleanResponse = response.trim();
                if (availableCategories.contains(cleanResponse)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Successfully classified request to category: " + cleanResponse);
                    }
                    return cleanResponse;
                }

                for (String categoryName : availableCategories) {
                    if (cleanResponse.toLowerCase().contains(categoryName.toLowerCase()) ||
                            categoryName.toLowerCase().contains(cleanResponse.toLowerCase())) {
                        if (log.isDebugEnabled()) {
                            log.debug("Matched request to category by partial match: " + categoryName);
                        }
                        return categoryName;
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Classification failed or returned invalid category, will use default model");
            }
            return null;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception during classification, will use default model: " + e.getMessage());
            }
            return null;
        }
    }

    private Set<String> getAvailableCategories(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        try {
            if (targetConfig == null || targetConfig.getCategories() == null) {
                return null;
            }

            Set<String> categories = new HashSet<>();
            Map<String, ModelEndpointDTO> categoryModels = targetConfig.getCategories();

            if (log.isDebugEnabled()) {
                log.debug("Available categories: " + categoryModels.keySet());
            }

            for (Map.Entry<String, ModelEndpointDTO> entry : categoryModels.entrySet()) {
                String categoryName = entry.getKey();
                ModelEndpointDTO modelData = entry.getValue();

                if (isValidModel(modelData)) {
                    categories.add(categoryName);
                    if (log.isDebugEnabled()) {
                        log.debug("Added valid category: " + categoryName);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipped invalid category: " + categoryName);
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Final valid categories: " + categories);
            }

            return categories.isEmpty() ? null : categories;
        } catch (Exception e) {
            return null;
        }
    }

    private String getEnvironment(MessageContext messageContext) {
        String apiKeyType = (String) messageContext.getProperty(APIConstants.API_KEY_TYPE);
        return APIConstants.API_KEY_TYPE_PRODUCTION.equals(apiKeyType) ? "production" : "sandbox";
    }

    private void logAllAvailableEndpoints(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig, String selectedCategory) {
        try {
            if (targetConfig == null || targetConfig.getCategories() == null) {
                log.debug("No target config or categories available");
                return;
            }

            Map<String, ModelEndpointDTO> categories = targetConfig.getCategories();
            log.debug("=== Available Categories and Endpoints ===");

            int categoryIndex = 0;
            for (Map.Entry<String, ModelEndpointDTO> entry : categories.entrySet()) {
                String categoryName = entry.getKey();
                ModelEndpointDTO modelData = entry.getValue();
                boolean isSelectedCategory = categoryName.equals(selectedCategory);

                log.debug("Category [" + categoryIndex + "]: " + categoryName +
                        (isSelectedCategory ? " (SELECTED)" : ""));

                if (modelData != null) {
                    log.debug("  Endpoint: ID=" + modelData.getEndpointId() + ", Model=" + modelData.getModel());
                }

                categoryIndex++;
            }

            if (targetConfig.getDefaultModel() != null) {
                log.debug("Default Model: ID=" + targetConfig.getDefaultModel().getEndpointId() + 
                         ", Model=" + targetConfig.getDefaultModel().getModel());
            }

            log.debug("=== End of Available Endpoints ===");
        } catch (Exception e) {
            log.debug("Error logging available endpoints: " + e.getMessage());
        }
    }

    private boolean isValidModel(ModelEndpointDTO model) {
        return model != null
                && model.getEndpointId() != null
                && !model.getEndpointId().trim().isEmpty()
                && model.getModel() != null
                && !model.getModel().trim().isEmpty();
    }

    @Override
    public boolean isContentAware() {
        return false;
    }

    public String getLlmRouteConfigs() {
        return llmRouteConfigs;
    }

    @Override
    public void destroy() {

    }

}



