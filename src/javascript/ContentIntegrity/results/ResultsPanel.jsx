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
import {COLUMNS, DEFAULT_FILTERS, DEFAULT_VISIBLE_COLUMNS, FILTERABLE_COLUMNS, formatCell, PAGE_SIZES, toFilterArgs} from './columns';
import {ErrorDetailsDialog} from './ErrorDetailsDialog';
import {RowActions} from './RowActions';
import {FixAllAction} from './FixAllAction';
import {useFixError} from './useFixError';
import {JcrBrowserLink} from '../common/JcrBrowserLink';
import {ReportLinks} from '../common/ReportLinks';
import styles from '../ContentIntegrity.scss';

const ALL = '__all__';
const ALL_WORKSPACES = 'all-workspaces';
const STATUS_COLORS = {running: 'accent', finished: 'success', interrupted: 'warning', failed: 'danger'};
// The message which replaces the errors of the results of a scan which is not finished
const STATUS_MESSAGES = {running: 'label.results.runningScan', interrupted: 'label.results.interruptedScan', failed: 'label.results.failedScan'};
const NO_FILTERS = {};
const DATE_WITH_MILLISECONDS = {year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3};
// The default filter shows only the errors which block an XML import, when there are some: otherwise it would hide all of them
const defaultFiltersOf = summary => (summary?.importErrorCount > 0 ? DEFAULT_FILTERS : NO_FILTERS);
const EMPTY = '__empty__';
const toOptionValue = name => (name === null || name === undefined || name === '' ? EMPTY : String(name));
const fromOptionValue = value => (value === EMPTY ? '' : value);

const FilterDropdown = ({column, values, active, onChange}) => {
    const {t} = useTranslation('content-integrity');
    const options = (values || []).map(v => ({
        value: toOptionValue(v.name),
        label: `${toOptionValue(v.name) === EMPTY ? t('label.results.empty') : v.name} (${v.count})`
    }));
    // Keep the active value selectable even when the other filters leave it no match.
    if (active !== undefined && !options.some(o => o.value === toOptionValue(active))) {
        options.push({value: toOptionValue(active), label: `${toOptionValue(active) === EMPTY ? t('label.results.empty') : active} (0)`});
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

export const ResultsPanel = ({requestedResultsId, refreshCount, isScanLocked, onRequestConsumed, onNewScan}) => {
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
    const [detailsId, setDetailsId] = useState(null);

    // A scan that just ended is not in the list yet: refresh it before selecting the requested results.
    const {refetch: refetchList} = list;
    useEffect(() => {
        if (requestedResultsId) {
            refetchList();
        }
    }, [requestedResultsId, refetchList]);

    // A scan that just ended wins, then the current selection if still stored, then the latest result.
    useEffect(() => {
        if (requestedResultsId && ids.includes(requestedResultsId)) {
            setResultsId(requestedResultsId);
            onRequestConsumed();
        } else if (!ids.includes(resultsId)) {
            // The latest results of a scan which is over: the card of a running scan already follows it
            const latest = [...summaries].reverse().find(s => s.status !== 'running') || summaries[summaries.length - 1];
            setResultsId(latest ? latest.id : null);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ids, requestedResultsId]);

    useEffect(() => {
        setPage(1);
    }, [resultsId]);

    const selectedSummary = summaries.find(s => s.id === resultsId);
    const filters = filterState.resultsId === resultsId ? filterState.filters : defaultFiltersOf(selectedSummary);
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
    const {fix, states: fixStates} = useFixError(resultsId, useCallback(() => refetchResults(), [refetchResults]));
    // The refresh button is in the page header: each click increments refreshCount.
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
        <div className={styles.panel}>
            <Card className={`${styles.card} ${styles.resultsCard}`}>
                <Typography variant="subheading" weight="bold" className={styles.sectionTitle}>{t('label.results.title')}</Typography>
                <div className={styles.toolbar}>
                    <div className={styles.filter}>
                        <Typography variant="caption" weight="semiBold" component="label">{t('label.results.scan')}</Typography>
                        <div className={styles.scanSelector}>
                            <Dropdown className={styles.scanDropdown}
                                      data={resultsData}
                                      value={resultsId || undefined}
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
                {!statusMessage && !hasErrors && <Typography className={styles.noError}>{t('label.results.noError')}</Typography>}
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
                                          onFixed={refetchResults}/>
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
                                            <TableHeadCell width="140px"><span className={styles.srOnly}>{t('label.results.actions')}</span></TableHeadCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {(details.errors || []).map(error => (
                                            <TableRow key={error.id} hasMultipleLines>
                                                {columns.map(c => (
                                                    <TableBodyCell key={c.key} className={styles.cell} width={c.width} title={String(formatCell(error[c.key]))}>
                                                        {c.jcrLink ? (
                                                            <JcrBrowserLink uuid={error.nodeId} workspace={error.workspace}>
                                                                {formatCell(error[c.key])}
                                                            </JcrBrowserLink>
                                                        ) : formatCell(error[c.key])}
                                                    </TableBodyCell>
                                                ))}
                                                <TableBodyCell width="140px">
                                                    <RowActions error={error} state={fixStates[error.id]} canFixErrors={canFixErrors} onFix={fix} onOpenDetails={setDetailsId}/>
                                                </TableBodyCell>
                                            </TableRow>
                                        ))}
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

            {detailsId && resultsId && (
                <ErrorDetailsDialog errorId={detailsId}
                                    resultsId={resultsId}
                                    fixState={fixStates[detailsId]}
                                    canFixErrors={canFixErrors}
                                    onFix={fix}
                                    onClose={() => setDetailsId(null)}/>
            )}
        </div>
    );
};

ResultsPanel.propTypes = {
    requestedResultsId: PropTypes.string,
    refreshCount: PropTypes.number.isRequired,
    isScanLocked: PropTypes.bool,
    onRequestConsumed: PropTypes.func.isRequired,
    onNewScan: PropTypes.func.isRequired
};
