/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import React, { FC, useState, useEffect } from 'react';
import { FormattedMessage } from 'react-intl';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Grid from '@mui/material/Grid';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import Button from '@mui/material/Button';
import AddCircle from '@mui/icons-material/AddCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import { styled } from '@mui/material/styles';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
import CloseIcon from '@mui/icons-material/Close';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import API from 'AppData/api';
import { Progress } from 'AppComponents/Shared';
import { useAPI } from 'AppComponents/Apis/Details/components/ApiContext';
import { Endpoint, ModelData, ModelVendor } from './Types';
import ModelCard from './ModelCard';

const CategoryCard: React.FC<{
    modelData: ModelData;
    modelList: ModelVendor[];
    endpointList: Endpoint[];
    onUpdate: (updatedModel: ModelData) => void;
    onDelete?: () => void;
}> = ({ modelData, modelList, endpointList, onUpdate, onDelete }) => {
    const [showNameInfo, setShowNameInfo] = useState(false);
    const [showContextInfo, setShowContextInfo] = useState(false);
    const [nameError, setNameError] = useState(false);
    const [contextError, setContextError] = useState(false);

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = event.target;
        const updatedModel = { ...modelData, [name]: value };

        // Clear errors when user starts typing
        if (name === 'name') {
            setNameError(false);
        }
        if (name === 'context') {
            setContextError(false);
        }

        if (name === 'endpointId') {
            const selectedEndpoint = endpointList.find((endpoint) => endpoint.id === value);
            if (selectedEndpoint) {
                updatedModel.endpointName = selectedEndpoint.name;
            }
        }

        onUpdate(updatedModel);
    };

    const validateFields = () => {
        const isNameEmpty = !modelData.name || modelData.name.trim() === '';
        const isContextEmpty = !modelData.context || modelData.context.trim() === '';
        
        setNameError(isNameEmpty);
        setContextError(isContextEmpty);
        
        return !isNameEmpty && !isContextEmpty;
    };

    // Validate when component updates
    React.useEffect(() => {
        if (modelData.name !== undefined || modelData.context !== undefined) {
            validateFields();
        }
    }, [modelData.name, modelData.context]);

    return (
        <Paper elevation={2} sx={{ padding: 2, margin: 1, position: 'relative' }}>
            <Grid item xs={12}>
                <Box sx={{ position: 'relative', mb: 1.5 }}>
                    <TextField
                        label="Category Name"
                        size="small"
                        fullWidth
                        name="name"
                        value={modelData.name || ''}
                        onChange={handleChange}
                        required
                        error={nameError}
                        helperText={nameError ? "Category name is required" : ""}
                        sx={{
                            '& .MuiInputBase-root': {
                                paddingRight: '40px'
                            }
                        }}
                    />
                    <IconButton
                        size="small"
                        color="primary"
                        onClick={() => setShowNameInfo(!showNameInfo)}
                        sx={{
                            position: 'absolute',
                            right: 8,
                            top: '4px',
                            transform: 'none'
                        }}
                    >
                        <InfoIcon fontSize="small" />
                    </IconButton>
                </Box>
                <Collapse in={showNameInfo}>
                    <Box
                        sx={{
                            backgroundColor: 'primary.main',
                            color: 'white',
                            p: 2,
                            pr: 5,
                            borderRadius: 1,
                            mb: 1,
                            position: 'relative',
                            boxShadow: 2,
                            minHeight: 48,
                            textAlign: 'left'
                        }}
                    >
                        <Typography variant="body2">
                            Enter a descriptive name for this category that helps identify the type of requests it will handle.
                        </Typography>
                        <IconButton
                            size="small"
                            onClick={() => setShowNameInfo(false)}
                            sx={{
                                position: 'absolute',
                                right: 8,
                                top: 8,
                                color: 'error.main',
                                backgroundColor: 'white',
                                width: 24,
                                height: 24,
                                '&:hover': {
                                    backgroundColor: 'grey.100'
                                }
                            }}
                        >
                            <CloseIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                    </Box>
                </Collapse>
                <Box sx={{ position: 'relative', mb: 1.5 }}>
                    <TextField
                        label="Category Context"
                        size="small"
                        fullWidth
                        name="context"
                        value={modelData.context || ''}
                        onChange={handleChange}
                        required
                        error={contextError}
                        helperText={contextError ? "Category context is required" : ""}
                        sx={{
                            '& .MuiInputBase-root': {
                                paddingRight: '40px'
                            }
                        }}
                    />
                    <IconButton
                        size="small"
                        color="primary"
                        onClick={() => setShowContextInfo(!showContextInfo)}
                        sx={{
                            position: 'absolute',
                            right: 8,
                            top: '4px',
                            transform: 'none'
                        }}
                    >
                        <InfoIcon fontSize="small" />
                    </IconButton>
                </Box>
                <Collapse in={showContextInfo}>
                    <Box
                        sx={{
                            backgroundColor: 'primary.main',
                            color: 'white',
                            p: 2,
                            pr: 5,
                            borderRadius: 1,
                            mb: 1.5,
                            position: 'relative',
                            boxShadow: 2,
                            minHeight: 48,
                            textAlign: 'left'
                        }}
                    >
                        <Typography variant="body2">
                            Define the context or criteria that determines when this category should be used for routing requests.
                        </Typography>
                        <IconButton
                            size="small"
                            onClick={() => setShowContextInfo(false)}
                            sx={{
                                position: 'absolute',
                                right: 8,
                                top: 8,
                                color: 'error.main',
                                backgroundColor: 'white',
                                width: 24,
                                height: 24,
                                '&:hover': {
                                    backgroundColor: 'grey.100'
                                }
                            }}
                        >
                            <CloseIcon sx={{ fontSize: 16 }} />
                        </IconButton>
                    </Box>
                </Collapse>
                <Grid container spacing={2}>
                    <Grid item xs={12}>
                    {modelList.length === 1 ? (
                        <FormControl size='small' fullWidth sx={{ mb: 1.5 }}>
                            <InputLabel id='model-label'>Model</InputLabel>
                            <Select
                                labelId='model-label'
                                id='model'
                                value={modelData.model}
                                label='Model'
                                name='model'
                                onChange={(e: any) => handleChange(e)}
                            >
                                {modelList[0].values.map((modelValue) => (
                                    <MenuItem key={modelValue} value={modelValue}>{modelValue}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    ) : (
                        <>
                            <FormControl size='small' fullWidth sx={{ mb: 1.5 }}>
                                <InputLabel id='vendor-label'>Provider</InputLabel>
                                <Select
                                    labelId='vendor-label'
                                    id='vendor'
                                    value={modelData.vendor}
                                    label='Provider'
                                    name='vendor'
                                    onChange={(e: any) => handleChange(e)}
                                >
                                    {modelList.map((vendor) => (
                                        <MenuItem key={vendor.vendor} value={vendor.vendor}>
                                            {vendor.vendor}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                            <FormControl size='small' fullWidth sx={{ mb: 1.5 }}>
                                <InputLabel id='model-label'>Model</InputLabel>
                                <Select
                                    labelId='model-label'
                                    id='model'
                                    value={modelData.model}
                                    label='Model'
                                    name='model'
                                    onChange={(e: any) => handleChange(e)}
                                >
                                    {modelList.find((modelEntry) => modelEntry.vendor === modelData.vendor)?.values.map((modelValue) => (
                                        <MenuItem key={modelValue} value={modelValue}>{modelValue}</MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </>
                    )}
                    <FormControl size='small' fullWidth sx={{ mb: 1.5 }}>
                        <InputLabel id='endpoint-label'>Endpoint</InputLabel>
                        <Select
                            labelId='endpoint-label'
                            id='endpoint'
                            value={modelData.endpointId}
                            label='Endpoint'
                            name='endpointId'
                            onChange={(e: any) => handleChange(e)}
                        >
                            {endpointList.map((endpoint) => (
                                <MenuItem key={endpoint.id} value={endpoint.id}>{endpoint.name}</MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    </Grid>
                    {onDelete && (
                        <Grid item xs={12} sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                            <IconButton color='error' onClick={onDelete} size="small">
                                <DeleteIcon />
                            </IconButton>
                        </Grid>
                    )}
                </Grid>
            </Grid>
        </Paper>
    );
};

import Alert from '@mui/material/Alert';
import { Link } from 'react-router-dom';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import CONSTS from 'AppData/Constants';

interface IntelligentRoutingConfig {
    production: {
        defaultModel: ModelData;
        categories: ModelData[];
    };
    sandbox: {
        defaultModel: ModelData;
        categories: ModelData[];
    };
    suspendDuration?: number;
}

interface IntelligentModelRoutingProps {
    setManualPolicyConfig: React.Dispatch<React.SetStateAction<string>>;
    manualPolicyConfig: string;
}

const StyledAccordionSummary = styled(AccordionSummary)(() => ({
    minHeight: 48,
    maxHeight: 48,
    '&.Mui-expanded': {
        minHeight: 48,
        maxHeight: 48,
    },
    '& .MuiAccordionSummary-content': {
        margin: 0,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        '&.Mui-expanded': {
            margin: 0,
        }
    }
}));

const IntelligentModelRouting: FC<IntelligentModelRoutingProps> = ({
    setManualPolicyConfig,
    manualPolicyConfig,
}) => {
    const [apiFromContext] = useAPI();
    const [config, setConfig] = useState<IntelligentRoutingConfig>({
        production: {
            defaultModel: {
                vendor: '',
                model: '',
                endpointId: '',
                endpointName: ''
            },
            categories: []
        },
        sandbox: {
            defaultModel: {
                vendor: '',
                model: '',
                endpointId: '',
                endpointName: ''
            },
            categories: []
        },
        suspendDuration: 0,
    });
    const [modelList, setModelList] = useState<ModelVendor[]>([]);
    const [productionEndpoints, setProductionEndpoints] = useState<Endpoint[]>([]);
    const [sandboxEndpoints, setSandboxEndpoints] = useState<Endpoint[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [productionEnabled, setProductionEnabled] = useState<boolean>(false);
    const [sandboxEnabled, setSandboxEnabled] = useState<boolean>(false);

    // Validation function to check if all categories are complete
    const areAllCategoriesComplete = (categories: ModelData[]) => {
        return categories.every(category => 
            category.name && category.name.trim() !== '' && 
            category.context && category.context.trim() !== ''
        );
    };

    const fetchEndpoints = () => {
        setLoading(true);
        const endpointsPromise = API.getApiEndpoints(apiFromContext.id);
        endpointsPromise
            .then((response) => {
                const endpoints = response.body.list;
                const defaultEndpoints = [];

                if (apiFromContext.endpointConfig?.production_endpoints) {
                    defaultEndpoints.push({
                        id: CONSTS.DEFAULT_ENDPOINT_ID.PRODUCTION,
                        name: 'Default Production Endpoint',
                        deploymentStage: 'PRODUCTION',
                        serviceUrl: apiFromContext.endpointConfig.production_endpoints.url || 
                                   (Array.isArray(apiFromContext.endpointConfig.production_endpoints) && 
                                    apiFromContext.endpointConfig.production_endpoints.length > 0 
                                    ? apiFromContext.endpointConfig.production_endpoints[0].url 
                                    : 'N/A'),
                    });
                }

                if (apiFromContext.endpointConfig?.sandbox_endpoints) {
                    defaultEndpoints.push({
                        id: CONSTS.DEFAULT_ENDPOINT_ID.SANDBOX,
                        name: 'Default Sandbox Endpoint',
                        deploymentStage: 'SANDBOX',
                        serviceUrl: apiFromContext.endpointConfig.sandbox_endpoints.url || 
                                   (Array.isArray(apiFromContext.endpointConfig.sandbox_endpoints) && 
                                    apiFromContext.endpointConfig.sandbox_endpoints.length > 0 
                                    ? apiFromContext.endpointConfig.sandbox_endpoints[0].url 
                                    : 'N/A'),
                    });
                }

                const allEndpoints = [...defaultEndpoints, ...endpoints];
                const prodEndpoints = allEndpoints.filter(ep => ep.deploymentStage === 'PRODUCTION');
                const sbEndpoints = allEndpoints.filter(ep => ep.deploymentStage === 'SANDBOX');

                setProductionEndpoints(prodEndpoints);
                setSandboxEndpoints(sbEndpoints);
            })
            .catch((error) => {
                console.error('Error fetching endpoints:', error);
            })
            .finally(() => {
                setLoading(false);
            });
    };

    const fetchModelList = () => {
        const modelListPromise = API.getLLMProviderModelList(JSON.parse(apiFromContext.subtypeConfiguration.configuration).llmProviderId);
        modelListPromise
            .then((response: any) => {
                const vendors: ModelVendor[] = response.body.map((vendor: any) => ({
                    vendor: vendor.name,
                    values: vendor.models
                }));
                setModelList(vendors);
            })
            .catch((error: any) => {
                console.error('Error fetching model list:', error);
            });
    };

    useEffect(() => {
        fetchEndpoints();
        fetchModelList();
    }, []);

    useEffect(() => {
        if (manualPolicyConfig !== '') {
            try {
                const parsedConfig = JSON.parse(manualPolicyConfig.replace(/'/g, '"'));
                setConfig(parsedConfig);
                
                // Set toggle states based on whether there's any configuration
                const hasProductionConfig = parsedConfig.production?.defaultModel?.model !== '' 
                    || parsedConfig.production?.categories?.length > 0;
                const hasSandboxConfig = parsedConfig.sandbox?.defaultModel?.model !== '' 
                    || parsedConfig.sandbox?.categories?.length > 0;
                
                setProductionEnabled(hasProductionConfig);
                setSandboxEnabled(hasSandboxConfig);
            } catch (error) {
                console.error('Error parsing manual policy config:', error);
            }
        }
    }, [manualPolicyConfig]);

    useEffect(() => {
        setManualPolicyConfig(JSON.stringify(config).replace(/"/g, "'"));
    }, [config]);

    const handleProductionToggle = (event: React.ChangeEvent<HTMLInputElement>) => {
        setProductionEnabled(event.target.checked);
        if (!event.target.checked) {
            setConfig(prev => ({
                ...prev,
                production: {
                    defaultModel: {
                        vendor: '',
                        model: '',
                        endpointId: '',
                        endpointName: '',
                    },
                    categories: [],
                },
            }));
        }
    };

    const handleSandboxToggle = (event: React.ChangeEvent<HTMLInputElement>) => {
        setSandboxEnabled(event.target.checked);
        if (!event.target.checked) {
            setConfig(prev => ({
                ...prev,
                sandbox: {
                    defaultModel: {
                        vendor: '',
                        model: '',
                        endpointId: '',
                        endpointName: '',
                    },
                    categories: [],
                },
            }));
        }
    };

    const handleAccordionChange = (env: 'production' | 'sandbox') => (event: React.SyntheticEvent, expanded: boolean) => {
        if (env === 'production') {
            handleProductionToggle({ target: { checked: expanded } } as React.ChangeEvent<HTMLInputElement>);
        } else {
            handleSandboxToggle({ target: { checked: expanded } } as React.ChangeEvent<HTMLInputElement>);
        }
    };

    const addCategory = (type: 'production' | 'sandbox') => {
        const newCategory: ModelData = {
            vendor: modelList.length > 0 ? modelList[0].vendor : '',
            model: modelList.length > 0 && modelList[0].values.length > 0 ? modelList[0].values[0] : '',
            endpointId: type === 'production' 
                ? (productionEndpoints.length > 0 ? productionEndpoints[0].id : '')
                : (sandboxEndpoints.length > 0 ? sandboxEndpoints[0].id : ''),
            endpointName: type === 'production' 
                ? (productionEndpoints.length > 0 ? productionEndpoints[0].name : '')
                : (sandboxEndpoints.length > 0 ? sandboxEndpoints[0].name : ''),
            name: '',
            context: ''
        };

        setConfig((prevConfig) => ({
            ...prevConfig,
            [type]: {
                ...prevConfig[type],
                categories: [...prevConfig[type].categories, newCategory]
            }
        }));
    };

    const updateDefaultModel = (type: 'production' | 'sandbox', updatedModel: ModelData) => {
        setConfig((prevConfig) => ({
            ...prevConfig,
            [type]: {
                ...prevConfig[type],
                defaultModel: updatedModel
            }
        }));
    };

    const updateCategory = (type: 'production' | 'sandbox', index: number, updatedModel: ModelData) => {
        setConfig((prevConfig) => ({
            ...prevConfig,
            [type]: {
                ...prevConfig[type],
                categories: prevConfig[type].categories.map((model, i) => i === index ? updatedModel : model)
            }
        }));
    };

    const deleteCategory = (type: 'production' | 'sandbox', index: number) => {
        setConfig((prevConfig) => ({
            ...prevConfig,
            [type]: {
                ...prevConfig[type],
                categories: prevConfig[type].categories.filter((_, i) => i !== index)
            }
        }));
    };


    if (loading) {
        return <Progress />;
    }

    return (
        <>
            <Grid item xs={12}>
                <Accordion 
                    expanded={productionEnabled} 
                    onChange={handleAccordionChange('production')}
                >
                    <StyledAccordionSummary
                        aria-controls='production-content'
                        id='production-header'
                    >
                        <Typography variant='subtitle2' color='textPrimary'>
                            <FormattedMessage
                                id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.production.title'
                                defaultMessage='Production Models'
                            />
                        </Typography>
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={productionEnabled}
                                    onChange={handleProductionToggle}
                                    name="production-toggle"
                                />
                            }
                            label=""
                            sx={{ mr: -1 }}
                        />
                    </StyledAccordionSummary>
                    <AccordionDetails>
                        {modelList.length === 0 && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                <FormattedMessage
                                    id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.no.models'
                                    defaultMessage='No models available. Please configure models for the LLM provider.'
                                />
                            </Alert>
                        )}
                        {productionEndpoints.length === 0 && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                <FormattedMessage
                                    id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.no.production.endpoints'
                                    defaultMessage='No production endpoints available. Please {configureLink} first.'
                                    values={{
                                        configureLink: (
                                            <Link to={`/apis/${apiFromContext.id}/endpoints`}>
                                                configure endpoints
                                            </Link>
                                        ),
                                    }}
                                />
                            </Alert>
                        )}
                        
                        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                            <Typography variant='subtitle2'>Default Model</Typography>
                            <Tooltip
                                title="Default model when no category matches"
                                placement="right"
                                arrow
                                componentsProps={{
                                    tooltip: {
                                        sx: {
                                            backgroundColor: 'primary.main',
                                            color: 'white',
                                            fontSize: '0.75rem',
                                            fontWeight: 500,
                                            maxWidth: 200,
                                            borderRadius: 1,
                                            boxShadow: 2,
                                            py: 1,
                                            px: 2
                                        }
                                    },
                                    arrow: {
                                        sx: {
                                            color: 'primary.main'
                                        }
                                    }
                                }}
                            >
                                <IconButton size="small" color="primary" sx={{ ml: 1 }}>
                                    <InfoIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        </Box>
                        <ModelCard
                            modelData={config.production.defaultModel}
                            modelList={modelList}
                            endpointList={productionEndpoints}
                            isWeightApplicable={false}
                            onUpdate={(updatedModel) => updateDefaultModel('production', updatedModel)}
                        />

                        <Typography variant='subtitle2' sx={{ mt: 3, mb: 1 }}>Categories</Typography>
                        {config.production.categories.length > 0 && !areAllCategoriesComplete(config.production.categories) && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                Please complete all category fields (name and context) before adding new categories or saving the configuration.
                            </Alert>
                        )}
                        <Button
                            variant='outlined'
                            color='primary'
                            data-testid='add-production-category'
                            sx={{ ml: 1, mb: 2 }}
                            onClick={() => addCategory('production')}
                            disabled={modelList.length === 0 || productionEndpoints.length === 0 || !areAllCategoriesComplete(config.production.categories)}
                            title={!areAllCategoriesComplete(config.production.categories) ? "Please complete all existing categories before adding a new one" : ""}
                        >
                            <AddCircle sx={{ mr: 1 }} />
                            <FormattedMessage
                                id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.add.category'
                                defaultMessage='Add Category'
                            />
                        </Button>
                        {config.production.categories.map((category, index) => (
                            <CategoryCard
                                key={index}
                                modelData={category}
                                modelList={modelList}
                                endpointList={productionEndpoints}
                                onUpdate={(updatedModel) => updateCategory('production', index, updatedModel)}
                                onDelete={() => deleteCategory('production', index)}
                            />
                        ))}
                    </AccordionDetails>
                </Accordion>
                
                <Accordion 
                    expanded={sandboxEnabled} 
                    onChange={handleAccordionChange('sandbox')}
                >
                    <StyledAccordionSummary
                        aria-controls='sandbox-content'
                        id='sandbox-header'
                    >
                        <Typography variant='subtitle2' color='textPrimary'>
                            <FormattedMessage
                                id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.sandbox.title'
                                defaultMessage='Sandbox Models'
                            />
                        </Typography>
                        <FormControlLabel
                            control={
                                <Switch
                                    checked={sandboxEnabled}
                                    onChange={handleSandboxToggle}
                                    name="sandbox-toggle"
                                />
                            }
                            label=""
                            sx={{ mr: -1 }}
                        />
                    </StyledAccordionSummary>
                    <AccordionDetails>
                        {modelList.length === 0 && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                <FormattedMessage
                                    id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.no.models'
                                    defaultMessage='No models available. Please configure models for the LLM provider.'
                                />
                            </Alert>
                        )}
                        {sandboxEndpoints.length === 0 && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                <FormattedMessage
                                    id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.no.sandbox.endpoints'
                                    defaultMessage='No sandbox endpoints available. Please {configureLink} first.'
                                    values={{
                                        configureLink: (
                                            <Link to={`/apis/${apiFromContext.id}/endpoints`}>
                                                configure endpoints
                                            </Link>
                                        ),
                                    }}
                                />
                            </Alert>
                        )}
                        
                        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                            <Typography variant='subtitle2'>Default Model</Typography>
                            <Tooltip
                                title="Default model when no category matches"
                                placement="right"
                                arrow
                                componentsProps={{
                                    tooltip: {
                                        sx: {
                                            backgroundColor: 'primary.main',
                                            color: 'white',
                                            fontSize: '0.75rem',
                                            fontWeight: 500,
                                            maxWidth: 200,
                                            borderRadius: 1,
                                            boxShadow: 2,
                                            py: 1,
                                            px: 2
                                        }
                                    },
                                    arrow: {
                                        sx: {
                                            color: 'primary.main'
                                        }
                                    }
                                }}
                            >
                                <IconButton size="small" color="primary" sx={{ ml: 1 }}>
                                    <InfoIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        </Box>
                        <ModelCard
                            modelData={config.sandbox.defaultModel}
                            modelList={modelList}
                            endpointList={sandboxEndpoints}
                            isWeightApplicable={false}
                            onUpdate={(updatedModel) => updateDefaultModel('sandbox', updatedModel)}
                        />

                        <Typography variant='subtitle2' sx={{ mt: 3, mb: 1 }}>Categories</Typography>
                        {config.sandbox.categories.length > 0 && !areAllCategoriesComplete(config.sandbox.categories) && (
                            <Alert severity="warning" sx={{ mb: 2 }}>
                                Please complete all category fields (name and context) before adding new categories or saving the configuration.
                            </Alert>
                        )}
                        <Button
                            variant='outlined'
                            color='primary'
                            data-testid='add-sandbox-category'
                            sx={{ ml: 1, mb: 2 }}
                            onClick={() => addCategory('sandbox')}
                            disabled={modelList.length === 0 || sandboxEndpoints.length === 0 || !areAllCategoriesComplete(config.sandbox.categories)}
                            title={!areAllCategoriesComplete(config.sandbox.categories) ? "Please complete all existing categories before adding a new one" : ""}
                        >
                            <AddCircle sx={{ mr: 1 }} />
                            <FormattedMessage
                                id='Apis.Details.Policies.CustomPolicies.IntelligentModelRouting.add.category'
                                defaultMessage='Add Category'
                            />
                        </Button>
                        {config.sandbox.categories.map((category, index) => (
                            <CategoryCard
                                key={index}
                                modelData={category}
                                modelList={modelList}
                                endpointList={sandboxEndpoints}
                                onUpdate={(updatedModel) => updateCategory('sandbox', index, updatedModel)}
                                onDelete={() => deleteCategory('sandbox', index)}
                            />
                        ))}
                    </AccordionDetails>
                </Accordion>
            </Grid>
        </>
    );
};

export default IntelligentModelRouting;
