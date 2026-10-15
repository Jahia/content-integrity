import React, {useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {
    Add,
    Button,
    ChevronDown,
    ChevronRight,
    Chip,
    Close,
    Dropdown,
    Loader,
    Table,
    TableBody,
    TableBodyCell,
    TableHead,
    TableHeadCell,
    TablePagination,
    TableRow,
    Typography
} from '@jahia/moonstone';
import {Card} from '../common/Card';
import {GET_SCAN_RESULTS, GET_SCAN_RESULTS_LIST, GET_SCAN_RESULTS_LOGS} from '../ContentIntegrity.gql';
import {ScanLogs} from '../scan/ScanLogs';
import {COLUMNS, DEFAULT_FILTERS, DEFAULT_VISIBLE_COLUMNS, FILTERABLE_COLUMNS, FIX_STATUS_FILTER, FIX_STATUS_VALUES, formatCell, PAGE_SIZES, toFilterArgs} from './columns';
import {NodeErrorsPanel} from './NodeErrorsPanel';
import {FixStatus} from './FixStatus';
import {FixAllAction} from './FixAllAction';
import {FIX_STATES, useFixError} from './useFixError';
import {JcrBrowserLink} from '../common/JcrBrowserLink';
import {ReportLinks} from '../common/ReportLinks';
import styles from '../ContentIntegrity.scss';

const ALL = '__all__';
const ALL_WORKSPACES = 'all-workspaces';
const STATUS_COLORS = {running: 'accent', finished: 'success', interrupted: 'warning', failed: 'danger'};
// The message which replaces the errors of the results of a scan which is not finished
const STATUS_MESSAGES = {running: 'label.results.runningScan', interrupted: 'label.results.interruptedScan', failed: 'label.results.failedScan'};
const NO_FILTERS = {};
const nodeKey = error => `${error.workspace}|${error.nodePath}`;
// The outcome of the fix of the errors of a node: running while one of them is fixed, failed when one of them has failed
const nodeFixState = (errors, states) => {
    if (errors.some(e => states[e.id] === FIX_STATES.FIXING)) {
        return FIX_STATES.FIXING;
    }

    return errors.some(e => states[e.id] === FIX_STATES.FAILED) ? FIX_STATES.FAILED : undefined;
};
const DATE_WITH_MILLISECONDS = {year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3};
// The default filter shows only the errors which block an XML import, when there are some: otherwise it would hide all of them
const defaultFiltersOf = summary => (summary?.importErrorCount > 0 ? DEFAULT_FILTERS : NO_FILTERS);
const EMPTY = '__empty__';
const toOptionValue = name => (name === null || name === undefined || name === '' ? EMPTY : String(name));
const fromOptionValue = value => (value === EMPTY ? '' : value);

const FilterDropdown = ({column, values, active, onChange}) => {
    const {t} = useTranslation('content-integrity');
    // The values of the fix status are keys, the other ones are displayed as they are
    const labelOf = name => {
        if (toOptionValue(name) === EMPTY) {
            return t('label.results.empty');
        }

        return column === FIX_STATUS_FILTER ? t(`label.fixStatus.${name}`) : name;
    };

    // A value which matches no error with the other filters is not offered, except the fix statuses, which are all offered
    const counted = column === FIX_STATUS_FILTER ?
        FIX_STATUS_VALUES.map(name => ({name, count: (values || []).find(v => v.name === name)?.count || 0})) :
        (values || []).filter(v => v.count > 0);
    const options = counted.map(v => ({value: toOptionValue(v.name), label: `${labelOf(v.name)} (${v.count})`}));
    // Keep the active value selectable even when the other filters leave it no match.
    if (active !== undefined && !options.some(o => o.value === toOptionValue(active))) {
        options.push({value: toOptionValue(active), label: `${labelOf(active)} (0)`});
    }

    const data = [{value: ALL, label: t('label.results.all')}, ...options];
    const current = active === undefined ? ALL : toOptionValue(active);
    return (
        <div className={styles.filter}>
            <Typography variant="caption" weight="semiBold" component="label">{t(`label.column.${column}`)}</Typography>
            <Dropdown data={data}
                      value={current}
                      variant="outlined"
                      size="small"
                      hasSearch={data.length > 10}
                      onChange={(e, item) => onChange(column, item.value === ALL ? undefined : fromOptionValue(item.value))}/>
        </div>
    );
};

FilterDropdown.propTypes = {
    column: PropTypes.string.isRequired,
    values: PropTypes.array,
    active: PropTypes.string,
    onChange: PropTypes.func.isRequired
};

export const ResultsPanel = ({selectsLatest, requestedResultsId, refreshCount, isScanLocked, onRequestConsumed, onNewScan}) => {
    const {t, i18n} = useTranslation('content-integrity');
    const list = useQuery(GET_SCAN_RESULTS_LIST, {fetchPolicy: 'network-only'});
    const summaries = useMemo(() => list.data?.integrity?.scanResults || [], [list.data]);
    const ids = useMemo(() => summaries.map(s => s.id), [summaries]);

    const [resultsId, setResultsId] = useState(null);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(20);
    // The filters belong to the displayed results: other results start again from their own default filters
    const [filterState, setFilterState] = useState({resultsId: null, filters: NO_FILTERS});
    const [visibleColumns, setVisibleColumns] = useState(DEFAULT_VISIBLE_COLUMNS);
    // The node whose errors are displayed beside the table, and the error of the clicked row
    const [selection, setSelection] = useState(null);
    // The nodes whose errors are hidden in the tree: all are expanded by default
    const [collapsed, setCollapsed] = useState(new Set());
    const toggleNode = key => setCollapsed(prev => {
        const next = new Set(prev);
        if (next.has(key)) {
            next.delete(key);
        } else {
            next.add(key);
        }

        return next;
    });

    // A scan that just ended is not in the list yet: refresh it before selecting the requested results.
    const {refetch: refetchList} = list;
    useEffect(() => {
        if (requestedResultsId) {
            refetchList();
        }
    }, [requestedResultsId, refetchList]);

    // A scan that just ended wins, then the current selection if still stored, then the latest result, unless the results
    // were cleared by Refresh: then none is displayed until a scan is chosen.
    useEffect(() => {
        if (requestedResultsId && ids.includes(requestedResultsId)) {
            setResultsId(requestedResultsId);
            onRequestConsumed();
        } else if (!ids.includes(resultsId)) {
            // The latest results of a scan which is over: the card of a running scan already follows it
            const latest = [...summaries].reverse().find(s => s.status !== 'running') || summaries[summaries.length - 1];
            setResultsId(latest && selectsLatest ? latest.id : null);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ids, requestedResultsId]);

    useEffect(() => {
        setPage(1);
        setSelection(null);
        setCollapsed(new Set());
    }, [resultsId]);

    const selectedSummary = summaries.find(s => s.id === resultsId);
    const filters = filterState.resultsId === resultsId ? filterState.filters : defaultFiltersOf(selectedSummary);
    // The panel displays the errors of a node in one workspace: filtered on another workspace, the table no longer displays
    // this node, so the panel is closed
    useEffect(() => {
        if (filters.workspace !== undefined && selection && selection.workspace !== filters.workspace) {
            setSelection(null);
        }
    }, [filters.workspace, selection]);
    const setFilters = update => setFilterState({resultsId, filters: typeof update === 'function' ? update(filters) : update});
    // Only the errors of a finished scan are displayed, so read: those of an interrupted scan do not cover all the
    // content, and a running or failed scan has none. A scan without error has nothing to filter: only its outcome is displayed.
    // The log stored with the results, read when displayed. It tells why a scan failed, so it is open for one
    const [logsState, setLogsState] = useState({resultsId: null, isExpanded: false});
    const isLogsExpanded = logsState.resultsId === resultsId ? logsState.isExpanded : selectedSummary?.status === 'failed';
    const logs = useQuery(GET_SCAN_RESULTS_LOGS, {skip: !resultsId || !isLogsExpanded, fetchPolicy: 'network-only', variables: {id: resultsId}});
    const statusMessage = STATUS_MESSAGES[selectedSummary?.status];
    const hasErrors = selectedSummary?.errorCount > 0;
    const showErrors = !statusMessage && hasErrors;
    const filterArgs = useMemo(() => toFilterArgs(filters), [filters]);
    const results = useQuery(GET_SCAN_RESULTS, {
        skip: !resultsId || !showErrors,
        fetchPolicy: 'network-only',
        variables: {
            id: resultsId,
            offset: (page - 1) * pageSize,
            size: pageSize,
            filters: filterArgs,
            filterColumns: FILTERABLE_COLUMNS
        }
    });
    // Keep the previous page on screen while the next one loads, so the table does not flash.
    const details = (results.data ?? results.previousData)?.integrity?.results;
    // The fix actions require the permission adminContentIntegrityFix, which the access to the module does not grant
    const canFixErrors = (results.data ?? results.previousData)?.integrity?.canFixErrors === true;
    // After a fix, the errors are read again: their 'fixed' and 'fixable' fields have changed
    const {refetch: refetchResults} = results;
    const {fix, states: fixStates, markFailed} = useFixError(resultsId, useCallback(() => refetchResults(), [refetchResults]));
    // A scan which ends without storing results increments refreshCount: the list and the errors are read again
    useEffect(() => {
        if (refreshCount > 0) {
            refetchList();
            if (resultsId) {
                refetchResults();
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [refreshCount]);

    const possibleValues = useMemo(
        () => Object.fromEntries((details?.possibleValues || []).map(c => [c.name, c.values])),
        [details]
    );

    const onFilterChange = (column, value) => {
        setFilters(prev => ({...prev, [column]: value}));
        setPage(1);
    };

    const columns = COLUMNS.filter(c => visibleColumns.includes(c.key));
    // A click on a row, or Enter on it, displays the errors of its node beside the table, the ones of a header row or the
    // one of an error row. A click on a link of the row does what the link does.
    const select = (event, workspace, path, errorId) => {
        if (event.target.closest('a, button')) {
            return;
        }

        setSelection({workspace, path, errorId});
    };

    const onRowKeyDown = (event, workspace, path, errorId) => {
        if ((event.key === 'Enter' || event.key === ' ') && event.target === event.currentTarget) {
            event.preventDefault();
            setSelection({workspace, path, errorId});
        }
    };

    const isSelectedNode = (workspace, path) => selection !== null && selection.workspace === workspace && selection.path === path;

    const closeDetails = () => {
        const closed = selection;
        setSelection(null);
        // The focus goes back to the row which opened the panel, if it is still displayed
        const row = closed.errorId ?
            document.querySelector(`tr[data-error-id="${CSS.escape(closed.errorId)}"]`) :
            document.querySelector(`tr[data-node-path="${CSS.escape(closed.path)}"][data-workspace="${CSS.escape(closed.workspace)}"][data-node-group]`);
        row?.focus();
    };

    // The errors of a node follow each other, sorted by workspace then by path: a header row starts each node
    // The errors are displayed as a tree: a row per node, sorted by workspace then by path, then the rows of its errors
    const nodes = [];
    (details?.errors || []).forEach((error, i, errors) => {
        if (i === 0 || errors[i - 1].workspace !== error.workspace || errors[i - 1].nodePath !== error.nodePath) {
            nodes.push({key: nodeKey(error), error, errors: []});
        }

        nodes[nodes.length - 1].errors.push(error);
    });
    // The column of the tree: the path when displayed, which the row of a node tells, otherwise the first column
    const treeColumn = columns.some(c => c.key === 'nodePath') ? 'nodePath' : columns[0]?.key;
    // The row expected by the cells of Moonstone in the column of the tree, as react-table describes it
    const treeRow = (depth, node) => ({
        depth,
        canExpand: Boolean(node),
        isExpanded: Boolean(node) && !collapsed.has(node.key),
        getToggleRowExpandedProps: props => ({
            ...props,
            role: 'button',
            tabIndex: 0,
            title: t(collapsed.has(node.key) ? 'label.results.expand' : 'label.results.collapse'),
            'aria-label': t(collapsed.has(node.key) ? 'label.results.expand' : 'label.results.collapse'),
            'aria-expanded': !collapsed.has(node.key),
            className: styles.treeToggle,
            onClick: event => {
                // The link of the path, in the same cell, opens the JCR browser
                if (event.target.closest('a, button')) {
                    return;
                }

                event.stopPropagation();
                toggleNode(node.key);
            },
            onKeyDown: event => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    event.stopPropagation();
                    toggleNode(node.key);
                }
            }
        })
    });
    const cellValue = (error, c) => (c.jcrLink ? (
        <JcrBrowserLink uuid={error.nodeId} workspace={error.workspace} title={String(formatCell(error[c.key]))}>{formatCell(error[c.key])}</JcrBrowserLink>
    ) : formatCell(error[c.key]));
    // The row of an error. The only error of a node is displayed alone, without the row of its node: its row tells the path
    // and the other properties of the node.
    const renderErrorRow = (error, isAlone) => {
        return (
            <TableRow key={error.id}
                      isSelected={isSelectedNode(error.workspace, error.nodePath)}
                      className={`${styles.clickableRow} ${error.id === selection?.errorId ? styles.focusedRow : ''}`}
                      // Moonstone 1.6 (Jahia 8.1.5) does not make the rows focusable
                      tabIndex={0}
                      data-node-group={isAlone ? true : undefined}
                      data-error-id={error.id}
                      data-node-path={error.nodePath}
                      data-workspace={error.workspace}
                      onClick={event => select(event, error.workspace, error.nodePath, error.id)}
                      onKeyDown={event => onRowKeyDown(event, error.workspace, error.nodePath, error.id)}>
                {columns.map(c => (
                    <TableBodyCell key={c.key}
                                   className={styles.cell}
                                   width={c.width}
                                   isExpandableColumn={c.key === treeColumn}
                                   row={c.key === treeColumn ? treeRow(isAlone ? 0 : 1) : undefined}
                                   title={String(formatCell(error[c.key]))}>
                        {cellValue(error, c)}
                    </TableBodyCell>
                ))}
                <TableBodyCell width="110px" textAlign="right" className={styles.fixStatusCell}>
                    <FixStatus error={error} state={fixStates[error.id]}/>
                </TableBodyCell>
            </TableRow>
        );
    };

    const activeFilterCount = Object.values(filters).filter(v => v !== undefined).length;
    const columnsData = COLUMNS.map(c => ({value: c.key, label: t(`label.column.${c.key}`)}));

    if (list.loading && !list.data) {
        return <div className={styles.centered}><Loader size="big"/></div>;
    }

    if (list.error) {
        return <Typography className={styles.error}>{list.error.message}</Typography>;
    }

    if (ids.length === 0) {
        return (
            <Card className={`${styles.card} ${styles.resultsCard}`}>
                <div className={styles.emptyState}>
                    <Typography variant="heading">{t('label.results.noResultsTitle')}</Typography>
                    <Typography variant="body" className={styles.helper}>{t('label.results.noResults')}</Typography>
                    <Button label={t('label.scan.newScan')} icon={<Add/>} color="accent" isDisabled={isScanLocked} onClick={onNewScan}/>
                </div>
            </Card>
        );
    }

    // The latest results first, named by the date of their scan, which the ID only encodes
    const labelOf = (s, withMilliseconds) => t('label.results.scanOption', {
        date: new Date(s.startDate).toLocaleString(i18n.language, withMilliseconds ? DATE_WITH_MILLISECONDS : undefined),
        workspace: s.workspace === ALL_WORKSPACES ? t('label.results.allWorkspaces') : s.workspace
    });
    const labels = summaries.map(s => labelOf(s, false));
    // Two scans started within the same second would have the same label: their milliseconds tell them apart
    const resultsData = summaries.map((s, i) => ({
        value: s.id,
        label: labels.indexOf(labels[i]) === labels.lastIndexOf(labels[i]) ? labels[i] : labelOf(s, true)
    })).reverse();
    const errorCount = details?.errorCount ?? 0;
    const totalErrorCount = details?.totalErrorCount ?? 0;

    return (
        <div className={`${styles.panel} ${selection && showErrors ? styles.panelWithDetails : ''}`}>
            <Card className={`${styles.card} ${styles.resultsCard}`}>
                <Typography variant="subheading" weight="bold" className={styles.sectionTitle}>{t('label.results.title')}</Typography>
                <div className={styles.toolbar}>
                    <div className={styles.filter}>
                        <Typography variant="caption" weight="semiBold" component="label">{t('label.results.scan')}</Typography>
                        <div className={styles.scanSelector}>
                            <Dropdown className={styles.scanDropdown}
                                      data={resultsData}
                                      value={resultsId || undefined}
                                      placeholder={t('label.results.selectScan')}
                                      variant="outlined"
                                      hasSearch={resultsData.length > 10}
                                      onChange={(e, item) => setResultsId(item.value)}/>
                            {selectedSummary && (
                                <Chip id="ci-scan-status"
                                      label={t(`label.results.status.${selectedSummary.status}`, selectedSummary.status)}
                                      color={STATUS_COLORS[selectedSummary.status] || 'default'}/>
                            )}
                            {selectedSummary && (
                                <Button label={t(isLogsExpanded ? 'label.execution.hideLogs' : 'label.execution.showLogs')}
                                        icon={isLogsExpanded ? <ChevronDown/> : <ChevronRight/>}
                                        variant="ghost"
                                        aria-expanded={isLogsExpanded}
                                        aria-controls="ci-results-logs"
                                        onClick={() => setLogsState({resultsId, isExpanded: !isLogsExpanded})}/>
                            )}
                        </div>
                    </div>
                    {showErrors && details && <ReportLinks reports={details.reports}/>}
                    <div className={styles.spacer}/>
                    {showErrors && (
                        <div className={styles.filter}>
                            <Typography variant="caption" weight="semiBold" component="label">{t('label.results.columns')}</Typography>
                            <Dropdown data={columnsData}
                                      values={visibleColumns}
                                      placeholder={t('label.results.columnsCount', {count: visibleColumns.length})}
                                      variant="outlined"
                                      onChange={(e, item) => setVisibleColumns(prev => (prev.includes(item.value) ?
                                          prev.filter(k => k !== item.value) :
                                          COLUMNS.map(c => c.key).filter(k => prev.includes(k) || k === item.value)))}/>
                        </div>
                    )}
                </div>
                {isLogsExpanded && (
                    <div id="ci-results-logs" className={styles.resultsLogs}>
                        {logs.loading && !logs.data ? <Loader size="small"/> : <ScanLogs logs={logs.data?.integrity?.logs || []}/>}
                        {logs.error && <Typography className={styles.error}>{logs.error.message}</Typography>}
                    </div>
                )}
                {statusMessage && <Typography className={styles.noError}>{t(statusMessage)}</Typography>}
                {selectedSummary && !statusMessage && !hasErrors && <Typography className={styles.noError}>{t('label.results.noError')}</Typography>}
                {showErrors && (
                    <>
                    <div id="ci-filters">
                        <div className={styles.filters}>
                            {FILTERABLE_COLUMNS.map(column => (
                                <FilterDropdown key={column}
                                                column={column}
                                                values={possibleValues[column]}
                                                active={filters[column]}
                                                onChange={onFilterChange}/>
                            ))}
                        </div>
                        {activeFilterCount > 0 && (
                            <Button className={styles.clearFilters}
                                    label={t('label.results.clearFilters')}
                                    icon={<Close/>}
                                    variant="ghost"
                                    onClick={() => {
                                        setFilters({});
                                        setPage(1);
                                    }}/>
                        )}
                    </div>

                    <div className={`${styles.sectionHeader} ${styles.resultsHeader}`}>
                        <Typography variant="subheading" weight="bold">
                            {errorCount === totalErrorCount ?
                                t('label.results.errorCount', {count: errorCount}) :
                                t('label.results.errorCountFiltered', {count: errorCount, total: totalErrorCount})}
                        </Typography>
                        {results.loading && <Loader size="small"/>}
                        <div className={styles.spacer}/>
                        {details && canFixErrors && (
                            // Remounted when the results or the filters change, so that the outcome of a fix all is not shown for other errors
                            <FixAllAction key={`${resultsId}|${filterArgs.join('|')}`}
                                          resultsId={resultsId}
                                          filterArgs={filterArgs}
                                          errorCount={errorCount}
                                          onFixed={result => {
                                              markFailed(result?.failedIds);
                                              refetchResults();
                                          }}/>
                        )}
                    </div>
                    {results.error && <Typography className={styles.error}>{results.error.message}</Typography>}
                    {details && (
                        <>
                            <div className={styles.tableWrapper}>
                                <Table className={styles.table} aria-label={t('label.results.tableLabel')}>
                                    <TableHead>
                                        <TableRow>
                                            {columns.map(c => <TableHeadCell key={c.key} width={c.width}>{t(`label.column.${c.key}`)}</TableHeadCell>)}
                                            <TableHeadCell width="110px" textAlign="right" className={styles.fixStatusHeadCell}><span className={styles.srOnly}>{t('label.results.fixStatus')}</span></TableHeadCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {nodes.map(node => (node.errors.length === 1 ? renderErrorRow(node.errors[0], true) : (
                                            <React.Fragment key={node.key}>
                                                <TableRow className={`${styles.clickableRow} ${styles.nodeRow}`}
                                                          isSelected={isSelectedNode(node.error.workspace, node.error.nodePath)}
                                                          tabIndex={0}
                                                          data-node-group
                                                          data-node-path={node.error.nodePath}
                                                          data-workspace={node.error.workspace}
                                                          aria-label={t('label.results.nodeGroup', {path: node.error.nodePath, workspace: node.error.workspace})}
                                                          onClick={event => select(event, node.error.workspace, node.error.nodePath, null)}
                                                          onKeyDown={event => onRowKeyDown(event, node.error.workspace, node.error.nodePath, null)}>
                                                    {columns.map(c => (
                                                        <TableBodyCell key={c.key}
                                                                       className={styles.cell}
                                                                       width={c.width}
                                                                       isExpandableColumn={c.key === treeColumn}
                                                                       row={c.key === treeColumn ? treeRow(0, node) : undefined}
                                                                       title={c.key === treeColumn || c.node ? String(formatCell(node.error[c.key === treeColumn ? 'nodePath' : c.key])) : undefined}>
                                                            {c.key === treeColumn && (
                                                                <JcrBrowserLink uuid={node.error.nodeId} workspace={node.error.workspace} title={node.error.nodePath}>{node.error.nodePath}</JcrBrowserLink>
                                                            )}
                                                            {c.key !== treeColumn && c.node && cellValue(node.error, c)}
                                                        </TableBodyCell>
                                                    ))}
                                                    <TableBodyCell width="110px" textAlign="right" className={styles.fixStatusCell}>
                                                        <FixStatus error={{
                                                                       fixed: node.errors.every(e => e.fixed || fixStates[e.id] === FIX_STATES.FIXED),
                                                                       fixFailed: node.errors.some(e => e.fixFailed && !fixStates[e.id])
                                                                   }}
                                                                   state={nodeFixState(node.errors, fixStates)}/>
                                                    </TableBodyCell>
                                                </TableRow>
                                                {!collapsed.has(node.key) && node.errors.map(error => renderErrorRow(error, false))}
                                            </React.Fragment>
                                        )))}
                                    </TableBody>
                                </Table>
                            </div>
                            {errorCount > 0 && (
                                <TablePagination totalNumberOfRows={errorCount}
                                                 currentPage={page}
                                                 rowsPerPage={pageSize}
                                                 rowsPerPageOptions={PAGE_SIZES}
                                                 label={{rowsPerPage: t('label.results.rowsPerPage'), of: t('label.results.of')}}
                                                 onPageChange={setPage}
                                                 onRowsPerPageChange={size => {
                                                     setPageSize(size);
                                                     setPage(1);
                                                 }}/>
                            )}
                        </>
                    )}
                    </>
                )}
            </Card>

            {selection && resultsId && showErrors && (
                // Remounted for another node, so that the values typed for the errors of a node are not kept for another one
                <NodeErrorsPanel key={`${selection.workspace}|${selection.path}`}
                                 workspace={selection.workspace}
                                 path={selection.path}
                                 focusedErrorId={selection.errorId}
                                 resultsId={resultsId}
                                 fixStates={fixStates}
                                 canFixErrors={canFixErrors}
                                 onFix={fix}
                                 onClose={closeDetails}/>
            )}
        </div>
    );
};

ResultsPanel.propTypes = {
    requestedResultsId: PropTypes.string,
    selectsLatest: PropTypes.bool.isRequired,
    refreshCount: PropTypes.number.isRequired,
    isScanLocked: PropTypes.bool,
    onRequestConsumed: PropTypes.func.isRequired,
    onNewScan: PropTypes.func.isRequired
};
