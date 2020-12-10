package org.jahia.modules.contentintegrity.services.impl;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ContentIntegrityCheckConfigurationImpl implements ContentIntegrityCheckConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityCheckConfigurationImpl.class);

    private final Map<String,DefaultConfiguration> defaultParameters = new HashMap<>();
    private final Map<String,Object> customParameters = new HashMap<>();

    @Override
    public Set<String> getConfigurationNames() {
        return defaultParameters.keySet();
    }

    @Override
    public void declareDefaultParameter(String name, Object value, String description) {
        defaultParameters.put(name, new DefaultConfiguration(value, description));
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
        return defaultParameters.get(name).getValue();
    }

    @Override
    public String getDescription(String name) {
        if (!defaultParameters.containsKey(name)) return null;
        return defaultParameters.get(name).getDescription();
    }

    private class DefaultConfiguration {
        final String description;
        final Object value;

        private DefaultConfiguration(Object value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public Object getValue() {
            return value;
        }
    }
}
