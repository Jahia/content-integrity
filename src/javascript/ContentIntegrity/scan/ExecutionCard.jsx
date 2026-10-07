import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, ChevronDown, ChevronRight, Chip, Loader, Typography, ViewList} from '@jahia/moonstone';
import {Card} from '../common/Card';
import {ScanLogs} from './ScanLogs';
import {ReportLinks} from '../common/ReportLinks';
import styles from '../ContentIntegrity.scss';

const STATUS_COLORS = {running: 'accent', finished: 'success', interrupted: 'warning', failed: 'danger'};

// The scan started (or found running) in this session: its status, its logs and its reports.
export const ExecutionCard = ({execution, isRunning, error, displayedResultsId, onShowResults}) => {
    const {t} = useTranslation('content-integrity');
    const [logsExpanded, setLogsExpanded] = useState(isRunning);

    // Show the logs while a scan runs, fold them once it is over: the results take over.
    useEffect(() => {
        setLogsExpanded(isRunning);
    }, [isRunning]);

    if (!execution.id && !error) {
        return null;
    }

    const status = execution.status || '';
    return (
        <Card className={styles.card}>
            <section className={styles.section} aria-labelledby="ci-exec-title">
                <div className={styles.sectionHeader}>
                    <div className={styles.titleWithStatus}>
                        <Typography id="ci-exec-title" variant="subheading" weight="bold">
                            {t(isRunning ? 'label.execution.current' : 'label.execution.last')}
                        </Typography>
                        {status && <Chip label={t(`label.execution.status.${status}`, status)} color={STATUS_COLORS[status] || 'default'}/>}
                        {isRunning && <Loader size="small"/>}
                    </div>
                    {!isRunning && execution.resultsID && execution.resultsID !== displayedResultsId && (
                        <Button label={t('label.execution.showResults')}
                                icon={<ViewList/>}
                                variant="outlined"
                                onClick={() => onShowResults(execution.resultsID)}/>
                    )}
                </div>
                {error && <Typography className={styles.error}>{error}</Typography>}
                {execution.id && (
                    <>
                        <Typography variant="caption" className={styles.helper}>
                            {t(isRunning ? 'label.execution.runningHelper' : 'label.execution.id', {id: execution.id})}
                        </Typography>
                        <div className={styles.executionActions}>
                            <Button label={t(logsExpanded ? 'label.execution.hideLogs' : 'label.execution.showLogs')}
                                    icon={logsExpanded ? <ChevronDown/> : <ChevronRight/>}
                                    variant="ghost"
                                    aria-expanded={logsExpanded}
                                    aria-controls="ci-scan-logs"
                                    onClick={() => setLogsExpanded(v => !v)}/>
                        </div>
                        {logsExpanded && <div id="ci-scan-logs"><ScanLogs logs={execution.logs}/></div>}
                        {/* The results card already lists the reports of the scan it displays. */}
                        {!isRunning && execution.resultsID !== displayedResultsId && <ReportLinks reports={execution.reports}/>}
                    </>
                )}
            </section>
        </Card>
    );
};

ExecutionCard.propTypes = {
    execution: PropTypes.shape({
        id: PropTypes.string,
        status: PropTypes.string,
        logs: PropTypes.arrayOf(PropTypes.string),
        reports: PropTypes.array,
        resultsID: PropTypes.string
    }).isRequired,
    isRunning: PropTypes.bool,
    error: PropTypes.string,
    displayedResultsId: PropTypes.string,
    onShowResults: PropTypes.func.isRequired
};
