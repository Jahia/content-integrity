package org.jahia.modules.contentintegrity.api;

import java.util.Set;
import java.util.function.Function;

public interface ContentIntegrityCheckConfiguration {
    Set<String> getConfigurationNames();

    void declareDefaultParameter(String name, Object value, Function<String, Object> stringValueParser, String description);

    void setParameter(String name, String value) throws IllegalArgumentException;

    Object getParameter(String name) throws IllegalArgumentException;

    String getDescription(String name);
}
