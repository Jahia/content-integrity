package org.jahia.modules.contentintegrity.services.impl;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ContentIntegrityCheckConfigurationImpl implements ContentIntegrityCheckConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityCheckConfigurationImpl.class);

    private final Map<String,Object> defaultParameters = new HashMap<>();
    private final Map<String,Object> customParameters = new HashMap<>();

    @Override
    public Set<String> getConfigurationNames() {
        return defaultParameters.keySet();
    }

    @Override
    public void declareDefaultParameter(String name, Object value) {
        defaultParameters.put(name, value);
    }

    @Override
    public void setParameter(String name, Object value) throws IllegalArgumentException {
        if (!defaultParameters.containsKey(name)) throw new IllegalArgumentException(String.format("Illegal parameter: %s", name));
        if (value == null) customParameters.remove(name);
        else customParameters.put(name, value);
    }

    @Override
    public Object getParameter(String name) {
        if (!defaultParameters.containsKey(name)) throw new IllegalArgumentException(String.format("Illegal parameter: %s", name));
        if (customParameters.containsKey(name)) return customParameters.get(name);
        return defaultParameters.get(name);
    }
}
