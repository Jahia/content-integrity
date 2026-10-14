import {gql} from '@apollo/client';

// The content-integrity API exposes its actions (run, stop, configure) as Query fields, so every
// action below is a query sent with fetchPolicy 'no-cache'.

export const GET_CHECKS = gql`
    query ContentIntegrityChecks {
        integrity: contentIntegrity {
            checks: integrityChecks {
                id
                enabled
                configurable
                documentation
            }
        }
    }
`;

export const GET_CHECK_CONFIGURATIONS = gql`
    query ContentIntegrityCheckConfigurations($id: String!) {
        integrity: contentIntegrity {
            check: integrityCheckById(id: $id) {
                id
                configurations {
                    name
                    value
                    defaultValue
                    type
                    description
                    rank
                }
            }
        }
    }
`;

export const RESET_CHECK_CONFIGURATIONS = gql`
    query ContentIntegrityResetCheckConfigurations($id: String!) {
        integrity: contentIntegrity {
            check: integrityCheckById(id: $id) {
                resetAllConfigurations
            }
        }
    }
`;

// One aliased `configure` call per changed value, every name and value passed as a variable.
export const buildSaveConfigurationsQuery = count => {
    const indexes = [...Array(count).keys()];
    const variables = indexes.map(i => `$n${i}: String!, $v${i}: String!`).join(', ');
    const calls = indexes.map(i => `c${i}: configure(name: $n${i}, value: $v${i})`).join('\n');
    return gql`
        query ContentIntegritySaveCheckConfigurations($id: String!${count > 0 ? ', ' + variables : ''}) {
            integrity: contentIntegrity {
                check: integrityCheckById(id: $id) {
                    id
                    ${calls}
                }
            }
        }
    `;
};

export const RUN_SCAN = gql`
    query ContentIntegrityRunScan($path: String!, $excludedPaths: [String], $ws: WorkspaceToScan!, $checks: [String], $skipMP: Boolean) {
        integrity: contentIntegrity {
            scan: integrityScan {
                id: scan(startNode: $path, excludedPaths: $excludedPaths, workspace: $ws, checksToRun: $checks, skipMountPoints: $skipMP)
            }
        }
    }
`;

export const GET_CURRENT_SCAN = gql`
    query ContentIntegrityCurrentScan {
        integrity: contentIntegrity {
            scan: integrityScan {
                id
                status
            }
        }
    }
`;

export const GET_SCAN = gql`
    query ContentIntegrityScan($id: String!) {
        integrity: contentIntegrity {
            scan: integrityScan(id: $id) {
                id
                status
                startDate
                resultsID
                logs
            }
        }
    }
`;

export const STOP_SCAN = gql`
    query ContentIntegrityStopScan($id: String!) {
        integrity: contentIntegrity {
            scan: integrityScan(id: $id) {
                stopRunningScan
            }
        }
    }
`;

export const GET_SCAN_RESULTS_LIST = gql`
    query ContentIntegrityScanResultsList {
        integrity: contentIntegrity {
            scanResults: scanResultsSummaries {
                id
                startDate
                workspace
                status
                errorCount
                importErrorCount
            }
        }
    }
`;

export const GET_SCAN_RESULTS_LOGS = gql`
    query ContentIntegrityScanResultsLogs($id: String!) {
        integrity: contentIntegrity {
            logs: scanResultsLogs(id: $id)
        }
    }
`;

export const GET_SCAN_RESULTS = gql`
    query ContentIntegrityScanResults($id: String!, $offset: Int!, $size: Int!, $filters: [String]!, $filterColumns: [String]) {
        integrity: contentIntegrity {
            canFixErrors
            results: scanResultsDetails(id: $id, filters: $filters) {
                errorCount
                totalErrorCount
                reports {
                    name
                    location
                    uri
                    extension
                }
                errors(offset: $offset, pageSize: $size) {
                    id
                    checkName
                    errorType
                    workspace
                    site
                    nodePath
                    nodeId
                    nodePrimaryType
                    nodeMixins
                    locale
                    message
                    extraInfosString
                    importError
                    fixed
                    fixFailed
                    fixable
                    fixWithValues
                }
                possibleValues(names: $filterColumns, withErrorsOnly: false) {
                    name
                    values {
                        name
                        count
                    }
                }
            }
        }
    }
`;

// The errors of a node, whatever the filters: they are displayed and fixed together, since they can block each other
export const GET_NODE_ERRORS = gql`
    query ContentIntegrityNodeErrors($resultsID: String!, $workspace: String!, $path: String!) {
        integrity: contentIntegrity {
            results: scanResultsDetails(id: $resultsID) {
                errors: nodeErrors(workspace: $workspace, path: $path) {
                    id
                    checkName
                    workspace
                    locale
                    nodePath
                    nodeId
                    nodePrimaryType
                    nodeMixins
                    message
                    errorType
                    site
                    importError
                    fixed
                    fixFailed
                    fixable
                    virtualNode
                    nodeExists
                    fixWithValues
                    fixValues {
                        name
                        type
                        multiple
                        choices
                        choiceLabels
                        choiceGroups
                        description
                        constraints
                        defaultValues
                    }
                    extraInfos {
                        label
                        value
                    }
                }
            }
        }
    }
`;

// Fixes errors together, each with the fix of the check which has detected it: the values of the errors of a node are
// saved at once. 'fixed' tells, for each error, if its fix has succeeded.
export const FIX_ERRORS = gql`
    query ContentIntegrityFixErrors($resultsID: String!, $ids: [String]!, $values: [[String]]) {
        integrity: contentIntegrity {
            results: scanResultsDetails(id: $resultsID) {
                errors: fixErrors(ids: $ids, values: $values) {
                    id
                    fixed
                    fixFailed
                    fixFailureMessage
                    fixable
                    fixWithValues
                }
            }
        }
    }
`;

export const GET_ADMIN_PANEL_NODE = gql`
    query ContentIntegrityAdminPanelNode {
        jcr {
            nodesByQuery(query: "select * from [jnt:contentIntegrityAdminPanel]", limit: 1) {
                nodes {
                    uuid
                    workspace
                    path
                }
            }
        }
    }
`;

export const FIX_ALL_ERRORS = gql`
    query ContentIntegrityFixAllErrors($resultsID: String!, $filters: [String]!) {
        integrity: contentIntegrity {
            results: scanResultsDetails(id: $resultsID, filters: $filters) {
                fixAll: fixAllErrors {
                    fixed
                    failed
                    failedIds
                    skipped
                    alreadyFixed
                    skippedReasons {
                        reason
                        count
                    }
                    failures {
                        checkName
                        errorType
                        message
                        count
                    }
                }
            }
        }
    }
`;
