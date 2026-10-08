import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, ChevronDown, ChevronRight, Loader, Typography} from '@jahia/moonstone';
import {Card} from '../common/Card';
import {ScanLogs} from './ScanLogs';
import styles from '../ContentIntegrity.scss';

// The scan which runs, started from this page or found running when it opened. Once it is over, the results card displays its outcome.
export const CurrentScanCard = ({execution}) => {
    const {t, i18n} = useTranslation('content-integrity');
    const [logsExpanded, setLogsExpanded] = useState(true);
    const startDate = execution.startDate ? new Date(execution.startDate).toLocaleString(i18n.language) : null;

    return (
        <Card className={styles.card}>
            <section className={styles.section} aria-labelledby="ci-exec-title">
                <div className={styles.sectionHeader}>
                    <div className={styles.titleWithStatus}>
                        <Typography id="ci-exec-title" variant="subheading" weight="bold">{t('label.execution.current')}</Typography>
                        <Loader size="small"/>
                    </div>
                    <Button label={t(logsExpanded ? 'label.execution.hideLogs' : 'label.execution.showLogs')}
                            icon={logsExpanded ? <ChevronDown/> : <ChevronRight/>}
                            variant="ghost"
                            aria-expanded={logsExpanded}
                            aria-controls="ci-scan-logs"
                            onClick={() => setLogsExpanded(v => !v)}/>
                </div>
                {startDate && <Typography variant="caption" className={styles.helper}>{t('label.execution.startedOn', {date: startDate})}</Typography>}
                <Typography variant="caption" className={styles.helper}>{t('label.execution.background')}</Typography>
                {logsExpanded && <div id="ci-scan-logs"><ScanLogs logs={execution.logs}/></div>}
            </section>
        </Card>
    );
};

CurrentScanCard.propTypes = {
    execution: PropTypes.shape({
        startDate: PropTypes.string,
        logs: PropTypes.arrayOf(PropTypes.string)
    }).isRequired
};
