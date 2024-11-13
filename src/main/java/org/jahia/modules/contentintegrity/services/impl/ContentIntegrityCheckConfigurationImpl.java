package org.jahia.modules.contentintegrity.services.impl;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class ContentIntegrityCheckConfigurationImpl implements ContentIntegrityCheckConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityCheckConfigurationImpl.class);

    private final Map<String, DefaultConfiguration> defaultParameters = new HashMap<>();
    private final Map<String, Object> customParameters = new HashMap<>();

    @Override
    public Set<String> getConfigurationNames() {
        return Collections.unmodifiableSet(defaultParameters.keySet());
    }

    @Override
    public void declareDefaultParameter(String name, Object value, Function<String, Object> stringValueParser, String description) {
        if (defaultParameters.containsKey(name))
            throw new IllegalArgumentException(String.format("Already declared parameter: %s", name));

        defaultParameters.put(name, new DefaultConfiguration(value, stringValueParser, description, defaultParameters.size()));
    }

    @Override
    public void setParameter(String name, String value) throws IllegalArgumentException {
        if (!defaultParameters.containsKey(name))
            throw new IllegalArgumentException(String.format("Illegal parameter: %s", name));

        if (value == null) {
            customParameters.remove(name);
            return;
        }

        final Function<String, Object> parser = defaultParameters.get(name).getStringValueParser();
        final Object parsedValue;
        try {
            parsedValue = parser.apply(value);
        } catch (Exception e) {
            // Here, we keep the last valid custom value, if there's one
            // As an alternative, we could have executed customParameters.remove(name) to reset back to the default value
            throw new IllegalArgumentException(String.format("Invalid value for the parameter %s : %s", name, value), e);
        }
        customParameters.put(name, parsedValue);
    }

    @Override
    public Object getParameter(String name) {
        if (!defaultParameters.containsKey(name))
            throw new IllegalArgumentException(String.format("Illegal parameter: %s", name));
        if (customParameters.containsKey(name)) return customParameters.get(name);
        return defaultParameters.get(name).getValue();
    }

    @Override
    public Object getParameterDefaultValue(String name) throws IllegalArgumentException {
        if (!defaultParameters.containsKey(name))
            throw new IllegalArgumentException(String.format("Illegal parameter: %s", name));
        return defaultParameters.get(name).getValue();
    }

    @Override
    public String getDescription(String name) {
        return Optional.of(defaultParameters)
                .map(params -> params.get(name))
                .map(DefaultConfiguration::getDescription)
                .orElse(null);
    }

    @Override
    public int getRank(String name) {
        return Optional.of(defaultParameters)
                .map(params -> params.get(name))
                .map(DefaultConfiguration::getRank)
                .orElse(Integer.MAX_VALUE);
    }

    private static class DefaultConfiguration {
        final String description;
        final Object value;
        final Function<String, Object> stringValueParser;
        final int rank;

        static final Function<String, Object> NO_TRANSFORMATION = s -> s;

        private DefaultConfiguration(Object value, Function<String, Object> stringValueParser, String description, int rank) {
            this.value = value;
            this.stringValueParser = stringValueParser;
            this.description = description;
            this.rank = rank;
        }

        public String getDescription() {
            return description;
        }

        public Object getValue() {
            return value;
        }

        public Function<String, Object> getStringValueParser() {
            return Optional.ofNullable(stringValueParser).orElse(NO_TRANSFORMATION);
        }

        public int getRank() {
            return rank;
        }
    }

    public static final Function<String, Object> BOOLEAN_PARSER = Boolean::valueOf;

    public static final Function<String, Object> INTEGER_PARSER = s -> {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }
    };
}
