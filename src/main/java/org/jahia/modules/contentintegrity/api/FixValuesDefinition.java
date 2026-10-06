package org.jahia.modules.contentintegrity.api;

import java.util.Collections;
import java.util.List;

/**
 * Describes the values an administrator has to provide to fix an error, for example the value of a missing mandatory property.
 */
public class FixValuesDefinition {

    private final String name;
    private final String type;
    private final boolean multiple;
    private final List<String> choices;
    private final List<String> constraints;
    private final List<String> defaultValues;

    /**
     * @param name          the name of the value, for example a property name
     * @param type          the JCR type of the values, as named by {@link javax.jcr.PropertyType#nameFromValue(int)}
     * @param multiple      true if several values can be provided
     * @param choices       the only accepted values, empty if any value matching the constraints is accepted
     * @param constraints   the constraints the values have to match, as declared in the definition
     * @param defaultValues the values suggested by default
     */
    public FixValuesDefinition(String name, String type, boolean multiple, List<String> choices, List<String> constraints, List<String> defaultValues) {
        this.name = name;
        this.type = type;
        this.multiple = multiple;
        this.choices = choices == null ? Collections.emptyList() : choices;
        this.constraints = constraints == null ? Collections.emptyList() : constraints;
        this.defaultValues = defaultValues == null ? Collections.emptyList() : defaultValues;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public List<String> getChoices() {
        return choices;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public List<String> getDefaultValues() {
        return defaultValues;
    }
}
