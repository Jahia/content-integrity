// Columns of the errors table. `filterable` columns get a value filter fed by `possibleValues`,
// `jcrLink` columns open the node in the JCR browser.
export const COLUMNS = [
    {key: 'checkName', display: false, filterable: true},
    {key: 'errorType', display: false, filterable: true},
    {key: 'workspace', display: false, filterable: true},
    {key: 'site', display: true, filterable: true},
    {key: 'nodePath', display: true, jcrLink: true, width: '32%'},
    {key: 'nodeId', display: false, jcrLink: true},
    {key: 'nodePrimaryType', display: true, filterable: true},
    {key: 'nodeMixins', display: false},
    {key: 'locale', display: false, filterable: true},
    {key: 'message', display: true, filterable: true, width: '32%'},
    {key: 'extraInfosString', display: false},
    {key: 'importError', display: false, filterable: true}
];

export const FILTERABLE_COLUMNS = COLUMNS.filter(c => c.filterable).map(c => c.key);

export const DEFAULT_VISIBLE_COLUMNS = COLUMNS.filter(c => c.display).map(c => c.key);

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
