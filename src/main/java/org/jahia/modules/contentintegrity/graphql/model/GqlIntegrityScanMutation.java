package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDefaultValue;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.graphql.provider.dxm.util.GqlUtils;

import java.util.List;

@GraphQLName("IntegrityScanMutation")
public class GqlIntegrityScanMutation {

    private static final String PATH_DESC = "Path of the node from which to start the scan. If not defined, the root node is used";

    private final GqlIntegrityScan scan;

    public GqlIntegrityScanMutation(GqlIntegrityScan scan) {
        this.scan = scan;
    }

    @GraphQLField
    @GraphQLName("scan")
    @GraphQLDescription("Starts a scan, and returns the ID of its execution, which the query integrityScan and the subscription " +
            "contentIntegrityScan follow")
    public String startScan(@GraphQLName("workspace") @GraphQLNonNull GqlIntegrityService.Workspace workspace,
                            @GraphQLName("startNode") @GraphQLDescription(PATH_DESC) String path,
                            @GraphQLName("excludedPaths") List<String> excludedPaths,
                            @GraphQLName("skipMountPoints") @GraphQLDefaultValue(GqlUtils.SupplierFalse.class) Boolean skipMountPoints,
                            @GraphQLName("checksToRun") List<String> checksToRun) {
        return scan.startScan(workspace, path, excludedPaths, skipMountPoints, checksToRun);
    }

    @GraphQLField
    @GraphQLDescription("Stops the scan of this execution, and returns false if it does not run")
    public boolean stopRunningScan() {
        return scan.stopRunningScan();
    }
}
