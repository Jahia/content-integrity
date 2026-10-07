import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Add, Button, Cancel, Header, Reload} from '@jahia/moonstone';
import {PageLayout} from './common/PageLayout';
import {NewScanDialog} from './scan/NewScanDialog';
import {ExecutionCard} from './scan/ExecutionCard';
import {RUNNING, useScanExecution} from './scan/useScanExecution';
import {ResultsPanel} from './results/ResultsPanel';
import styles from './ContentIntegrity.scss';

/*
 * One page, no tabs: the results are the main content, a scan is configured in a dialog opened from
 * the header, and the scan of this session is followed in a card above the results.
 */
export const ContentIntegrityAdmin = () => {
    const {t} = useTranslation('content-integrity');
    const {execution, isRunning, isStarting, error, start, stop} = useScanExecution();
    const [isDialogOpen, setDialogOpen] = useState(false);
    const [requestedResultsId, setRequestedResultsId] = useState(null);
    const [displayedResultsId, setDisplayedResultsId] = useState(null);
    const [refreshCount, setRefreshCount] = useState(0);

    // When the followed scan ends, display its results.
    const previousStatus = useRef(execution.status);
    useEffect(() => {
        if (previousStatus.current === RUNNING && execution.status !== RUNNING && execution.resultsID) {
            setRequestedResultsId(execution.resultsID);
        }

        previousStatus.current = execution.status;
    }, [execution.status, execution.resultsID]);

    const openDialog = useCallback(() => setDialogOpen(true), []);
    const closeDialog = useCallback(() => setDialogOpen(false), []);
    const clearRequest = useCallback(() => setRequestedResultsId(null), []);
    const locked = isRunning || isStarting;

    return (
        <PageLayout
            header={(
                <Header title={t('label.settings.title')}
                        contentType={t('label.contentIntegrity.description')}
                        mainActions={(
                            <>
                                <Button label={t('label.results.refresh')}
                                        icon={<Reload/>}
                                        variant="ghost"
                                        size="big"
                                        onClick={() => setRefreshCount(c => c + 1)}/>
                                {isRunning && (
                                    <Button label={t('label.scan.stop')} icon={<Cancel/>} color="danger" variant="outlined" size="big" onClick={stop}/>
                                )}
                                <Button label={t('label.scan.newScan')}
                                        icon={<Add/>}
                                        color="accent"
                                        size="big"
                                        isLoading={isStarting}
                                        isDisabled={locked}
                                        onClick={openDialog}/>
                            </>
                        )}/>
            )}
            content={(
                <div className={styles.root}>
                    <div className={styles.panel}>
                        <ExecutionCard execution={execution}
                                       isRunning={isRunning}
                                       error={error}
                                       displayedResultsId={displayedResultsId}
                                       onShowResults={setRequestedResultsId}/>
                        <ResultsPanel requestedResultsId={requestedResultsId}
                                      refreshCount={refreshCount}
                                      isScanLocked={locked}
                                      onRequestConsumed={clearRequest}
                                      onResultsChange={setDisplayedResultsId}
                                      onNewScan={openDialog}/>
                    </div>
                    <NewScanDialog isOpen={isDialogOpen}
                                   onClose={closeDialog}
                                   onRun={params => {
                                       setDialogOpen(false);
                                       start(params);
                                   }}/>
                </div>
            )}
        />
    );
};
