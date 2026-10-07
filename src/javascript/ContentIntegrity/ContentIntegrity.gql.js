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
                id: scan(startNode: $path, excludedPaths: $excludedPaths, workspace: $ws, checksToRun: $checks, uploadResults: true, skipMountPoints: $skipMP)
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
                resultsID
                logs
                reports {
                    name
                    location
                    uri
                    extension
                }
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
            scanResults
        }
    }
`;

export const GET_SCAN_RESULTS = gql`
    query ContentIntegrityScanResults($id: String!, $offset: Int!, $size: Int!, $filters: [String]!, $filterColumns: [String]) {
        integrity: contentIntegrity {
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

export const GET_ERROR_DETAILS = gql`
    query ContentIntegrityErrorDetails($id: String!, $resultsID: String!) {
        integrity: contentIntegrity {
            results: scanResultsDetails(id: $resultsID) {
                error: errorById(id: $id) {
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
                    fixable
                    fixWithValues
                    fixValues {
                        name
                        type
                        multiple
                        choices
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

// Runs the fix of the check which has detected the error. 'fixed' tells if the fix has succeeded.
export const FIX_ERROR = gql`
    query ContentIntegrityFixError($resultsID: String!, $id: String!, $values: [String]) {
        integrity: contentIntegrity {
            results: scanResultsDetails(id: $resultsID) {
                error: fixError(id: $id, values: $values) {
                    id
                    fixed
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
                    skipped
                    alreadyFixed
                }
            }
        }
    }
`;
