package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.Utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GqlScanResultsError {

    private final ContentIntegrityError error;
    // Read once: the description of the values reads the node of the error
    private boolean fixValuesRead = false;
    private GqlFixValuesDefinition fixValues;

    public GqlScanResultsError(ContentIntegrityError error) {
        this.error = error;
    }

    @GraphQLField
    public String getCheckName() {
        return error.getIntegrityCheckName();
    }

    @GraphQLField
    public boolean isFixed() {
        return error.isFixed();
    }

    @GraphQLField
    @GraphQLDescription("True if the node of the error is virtual: served by an external provider, such as a mount point. Its errors are not fixed")
    public boolean isVirtualNode() {
        return Utils.isOnVirtualNode(error);
    }

    @GraphQLField
    @GraphQLDescription("True if the check which has detected the error provides a fix for it, the error is not fixed yet, and its node is not virtual")
    public boolean isFixable() {
        return !error.isFixed() && Utils.getContentIntegrityService().isFixable(error);
    }

    @GraphQLField
    @GraphQLDescription("True if the fix of the error takes values, which are described by the field 'fixValues'. Reads the node of the error")
    public boolean isFixWithValues() {
        return getFixValues() != null;
    }

    @GraphQLField
    @GraphQLDescription("The values to provide to fix the error, null if its fix doesn't take values. Reads the node of the error")
    public GqlFixValuesDefinition getFixValues() {
        if (!fixValuesRead) {
            fixValuesRead = true;
            fixValues = error.isFixed() ? null : Optional.ofNullable(Utils.getContentIntegrityService().getFixValuesDefinition(error))
                    .map(GqlFixValuesDefinition::new)
                    .orElse(null);
        }
        return fixValues;
    }

    @GraphQLField
    @GraphQLName("id")
    public String getErrorID() {
        return error.getErrorID();
    }

    @GraphQLField
    public String getErrorType() {
        return Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::getKey).orElse(StringUtils.EMPTY);
    }

    @GraphQLField
    public String getWorkspace() {
        return error.getWorkspace();
    }

    @GraphQLField
    public String getNodeId() {
        return error.getUuid();
    }

    @GraphQLField
    public String getNodePath() {
        return error.getPath();
    }

    @GraphQLField
    public String getSite() {
        return error.getSite();
    }

    @GraphQLField
    public String getNodePrimaryType() {
        return error.getPrimaryType();
    }

    @GraphQLField
    public String getNodeMixins() {
        return error.getMixins();
    }

    @GraphQLField
    public String getLocale() {
        return error.getLocale();
    }

    @GraphQLField
    public String getMessage() {
        return error.getConstraintMessage();
    }

    @GraphQLField
    public List<GqlScanResultsErrorExtraInfo> getExtraInfos() {
        return error.getAllExtraInfos().entrySet().stream()
                .map(e -> new GqlScanResultsErrorExtraInfo(e.getKey(), e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @GraphQLField
    public String getExtraInfosString() {
        final Map<String, Object> infos = error.getAllExtraInfos();
        if (MapUtils.isEmpty(infos)) return StringUtils.EMPTY;
        return infos.toString();
    }

    @GraphQLField
    public Boolean getImportError() {
        return Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::isBlockingImport).orElse(Boolean.FALSE);
    }
}
