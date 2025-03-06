package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.utils.Patterns;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jahia.api.Constants.WORKINPROGRESS_STATUS_DISABLED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIAMIX_MARKED_FOR_DELETION;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_LANGUAGES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.WORKINPROGRESS_STATUS;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class WorkspaceSpecificDefinitionsCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceSpecificDefinitionsCheck.class);

    public static final ContentIntegrityErrorType UNEXPECTED_TYPE = createErrorType("UNEXPECTED_TYPE", "The type is not allowed in this workspace", true);
    public static final ContentIntegrityErrorType UNEXPECTED_PROP = createErrorType("UNEXPECTED_PROP", "The property is not allowed in this workspace", true);
    public static final ContentIntegrityErrorType UNEXPECTED_PROP_VALUE = createErrorType("UNEXPECTED_PROP_VALUE", "The property is not allowed in this workspace with this value", true);

    private static final String OPERATOR_NOT_EQUAL = "!=";
    private static final String OPERATOR_EQUAL = "==";

    private static final String WS_DEFAULT_TYPES_KEY = "ws-default-specific-types";
    private static final String WS_DEFAULT_TYPES_DEFAULT_VAL = Stream.of(JAHIAMIX_MARKED_FOR_DELETION, JAHIAMIX_MARKED_FOR_DELETION_ROOT)
            .collect(Collectors.joining(Patterns.COMMA.pattern()));
    private static final String WS_DEFAULT_PROPERTIES_KEY = "ws-default-specific-props";
    private static final String WS_DEFAULT_PROPERTIES_DEFAULT_VAL = "";
    private static final String WS_DEFAULT_PROP_VALUES_KEY = "ws-default-specific-props-values";
    private static final String WS_DEFAULT_PROP_VALUES_DEFAULT_VAL = "";
    private static final String WS_LIVE_TYPES_KEY = "ws-live-specific-types";
    private static final String WS_LIVE_TYPES_DEFAULT_VAL = "";
    private static final String WS_LIVE_PROPERTIES_KEY = "ws-live-specific-props";
    private static final String WS_LIVE_PROPERTIES_DEFAULT_VAL = "";
    private static final String WS_LIVE_PROP_VALUES_KEY = "ws-live-specific-props-values";
    private static final String WS_LIVE_PROP_VALUES_DEFAULT_VAL = WORKINPROGRESS_STATUS + OPERATOR_EQUAL + WORKINPROGRESS_STATUS_DISABLED;

    private final ContentIntegrityCheckConfiguration configurations;

    private final Collection<String> types = new ArrayList<>();
    private final Collection<String> properties = new ArrayList<>();
    private final Collection<PropertyWithValue> propertyWithValues = new ArrayList<>();

    public WorkspaceSpecificDefinitionsCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(WS_DEFAULT_TYPES_KEY, WS_DEFAULT_TYPES_DEFAULT_VAL, null, "Types which should be defined only in the default workspace (comma separated, spaces allowed)");
        getConfigurations().declareDefaultParameter(WS_DEFAULT_PROPERTIES_KEY, WS_DEFAULT_PROPERTIES_DEFAULT_VAL, null, "Properties which should be defined only in the default workspace (comma separated, spaces allowed)");
        getConfigurations().declareDefaultParameter(WS_DEFAULT_PROP_VALUES_KEY, WS_DEFAULT_PROP_VALUES_DEFAULT_VAL, null, "Properties which should be defined in the default workspace only with the specified value (format 'propertyName==value') or only with a value different from the specified one (format 'propertyName==value') (comma separated, spaces allowed)");
        getConfigurations().declareDefaultParameter(WS_LIVE_TYPES_KEY, WS_LIVE_TYPES_DEFAULT_VAL, null, "Types which should be defined only in the live workspace (comma separated, spaces allowed)");
        getConfigurations().declareDefaultParameter(WS_LIVE_PROPERTIES_KEY, WS_LIVE_PROPERTIES_DEFAULT_VAL, null, "Properties which should be defined only in the live workspace (comma separated, spaces allowed)");
        getConfigurations().declareDefaultParameter(WS_LIVE_PROP_VALUES_KEY, WS_LIVE_PROP_VALUES_DEFAULT_VAL, null, "Properties which should be defined in the live workspace only with the specified value (format 'propertyName==value') or only with a value different from the specified one (format 'propertyName==value') (comma separated, spaces allowed)");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    @Override
    protected void reset() {
        types.clear();
        properties.clear();
        propertyWithValues.clear();
    }

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        final String confNameTypes, confNameProps, confNamePropsVals;
        final String workspace = JCRUtils.runJcrSupplierCallBack(() -> scanRootNode.getSession().getWorkspace().getName(), Constants.CALCULATION_ERROR);
        switch (workspace) {
            case Constants.EDIT_WORKSPACE:
                confNameTypes = WS_LIVE_TYPES_KEY;
                confNameProps = WS_LIVE_PROPERTIES_KEY;
                confNamePropsVals = WS_DEFAULT_PROP_VALUES_KEY;
                break;
            case Constants.LIVE_WORKSPACE:
                confNameTypes = WS_DEFAULT_TYPES_KEY;
                confNameProps = WS_DEFAULT_PROPERTIES_KEY;
                confNamePropsVals = WS_LIVE_PROP_VALUES_KEY;
                break;
            default:
                logger.error("Unsupported workspace {}", workspace);
                setScanDurationDisabled(true);
                return;
        }
        final String confTypes = (String) getConfigurations().getParameter(confNameTypes);
        Patterns.COMMA.splitAsStream(confTypes)
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .forEach(types::add);
        final String confProps = (String) getConfigurations().getParameter(confNameProps);
        Patterns.COMMA.splitAsStream(confProps)
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .forEach(properties::add);
        final String confPropsVals = (String) getConfigurations().getParameter(confNamePropsVals);
        Patterns.COMMA.splitAsStream(confPropsVals)
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(PropertyWithValue::new)
                .filter(PropertyWithValue::isValid)
                .forEach(propertyWithValues::add);

        if (types.isEmpty() && properties.isEmpty() && propertyWithValues.isEmpty()) setScanDurationDisabled(true);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        if (types.isEmpty() && properties.isEmpty() && propertyWithValues.isEmpty()) return null;

        final ContentIntegrityErrorList errorsList = createEmptyErrorsList();
        types.stream()
                .filter(type -> JCRUtils.runJcrCallBack(type, node::isNodeType, Boolean.FALSE))
                .map(type -> createError(node, UNEXPECTED_TYPE)
                        .addExtraInfo("unexpected-type", type))
                .forEach(errorsList::addError);
        properties.stream()
                .filter(prop -> JCRUtils.runJcrCallBack(prop, node::hasProperty, Boolean.FALSE))
                .map(prop -> createError(node, UNEXPECTED_PROP)
                        .addExtraInfo("unexpected-prop", prop))
                .forEach(errorsList::addError);
        propertyWithValues.stream()
                .filter(prop -> JCRUtils.runJcrCallBack(prop.getPropertyName(), node::hasProperty, Boolean.FALSE))
                .filter(prop -> !prop.isValueValid(node.getPropertyAsString(prop.getPropertyName())))
                .map(prop -> createError(node, UNEXPECTED_PROP_VALUE)
                        .addExtraInfo("unexpected-prop", prop.getPropertyName())
                        .addExtraInfo("unexpected-prop-value", node.getPropertyAsString(prop.getPropertyName())))
                .forEach(errorsList::addError);

        return errorsList;
    }

    private static class PropertyWithValue {
        private String propertyName, propertyValue;
        private boolean equalOperator;
        boolean isValid;

        public PropertyWithValue(String declaration) {
            final String operator;
            if (StringUtils.contains(declaration, OPERATOR_EQUAL)) {
                equalOperator = true;
                operator = OPERATOR_EQUAL;
            } else if (StringUtils.contains(declaration, OPERATOR_NOT_EQUAL)) {
                equalOperator = false;
                operator = OPERATOR_NOT_EQUAL;
            } else {
                isValid = false;
                return;
            }

            final String[] split = StringUtils.split(declaration, operator, 2);
            if (split.length != 2) {
                isValid = false;
                return;
            }

            propertyName = StringUtils.trimToNull(split[0]);
            propertyValue = StringUtils.trimToNull(split[1]);
            isValid = propertyName != null && propertyValue != null;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isValueValid(String value) {
            return equalOperator == StringUtils.equals(value, propertyValue);
        }
    }
}
