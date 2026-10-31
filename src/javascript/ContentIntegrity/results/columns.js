// Columns of the errors table. `filterable` columns get a value filter fed by `possibleValues`,
// `jcrLink` columns open the node in the JCR browser. The errors are displayed as a tree: a row per node, the errors of the
// node below it. The `node` columns describe the node: the row of the node displays only them, and the rows of its errors
// repeat them, so that each row tells where its error is.
export const COLUMNS = [
    {key: 'nodePath', display: true, jcrLink: true, width: '28%', node: true},
    {key: 'workspace', display: true, filterable: true, node: true},
    {key: 'checkName', display: true, filterable: true},
    {key: 'errorType', display: true, filterable: true},
    {key: 'site', display: false, filterable: true, node: true},
    {key: 'nodeId', display: false, jcrLink: true, node: true},
    {key: 'nodePrimaryType', display: false, filterable: true, node: true},
    {key: 'nodeMixins', display: false, node: true},
    {key: 'locale', display: false, filterable: true},
    {key: 'message', display: true, filterable: true, width: '24%'},
    {key: 'extraInfosString', display: false},
    {key: 'importError', display: false, filterable: true}
];

// The fix status is a filter, not a column: the last cell of a row tells it. Its values are the errors fixed, the ones on
// which no fix has run, and the ones whose fix has run without fixing them. All of them are offered, in this order.
export const FIX_STATUS_FILTER = 'fixStatus';
export const FIX_STATUS_VALUES = ['fixed', 'skipped', 'notFixed'];

export const FILTERABLE_COLUMNS = [...COLUMNS.filter(c => c.filterable).map(c => c.key), FIX_STATUS_FILTER];

export const DEFAULT_VISIBLE_COLUMNS = COLUMNS.filter(c => c.display).map(c => c.key);

// The results open on the errors which impact the XML import: they are the ones to fix first.
export const DEFAULT_FILTERS = {importError: 'true'};

export const PAGE_SIZES = [10, 20, 50, 100];

// The API takes each active filter as "<column>;<value>".
export const toFilterArgs = activeFilters => Object.entries(activeFilters)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${key};${value}`);

export const formatCell = value => {
    if (value === null || value === undefined) {
        return '';
    }

    return typeof value === 'boolean' ? String(value) : value;
};
