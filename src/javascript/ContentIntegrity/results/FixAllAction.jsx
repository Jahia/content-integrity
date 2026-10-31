import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Build, Button, Typography} from '@jahia/moonstone';
import {ConfirmDialog} from '../common/ConfirmDialog';
import {FIX_ALL_ERRORS} from '../ContentIntegrity.gql';
import {FixOutcomeDialog} from './FixOutcomeDialog';
import styles from '../ContentIntegrity.scss';

/**
 * Fixes all the errors matching the active filters, each with the fix of the check which has detected it. A fix can
 * remove content, so the administrator confirms first, once they have ticked that the data are going to be altered. The outcome is displayed in a dialog, which tells why errors are
 * skipped or not fixed, and is passed to onFixed, which tags the errors not fixed.
 */
export const FixAllAction = ({resultsId, filterArgs, errorCount, onFixed}) => {
    const {t} = useTranslation('content-integrity');
    const client = useApolloClient();
    const [isConfirming, setConfirming] = useState(false);
    const [isFixing, setFixing] = useState(false);
    const [outcome, setOutcome] = useState(null);

    const fixAll = () => {
        setFixing(true);
        setOutcome(null);
        client.mutate({mutation: FIX_ALL_ERRORS, variables: {resultsID: resultsId, filters: filterArgs}, fetchPolicy: 'no-cache'})
            .then(({data}) => {
                const result = data?.integrity?.results?.fixAll;
                // The errors already fixed are skipped as well
                const skipped = [{reason: 'ALREADY_FIXED', count: result.alreadyFixed}, ...result.skippedReasons];
                setOutcome({result: {fixed: result.fixed, skipped, failures: result.failures}});
                onFixed(result);
            })
            .catch(e => setOutcome({error: e.message}))
            .finally(() => {
                setFixing(false);
                setConfirming(false);
            });
    };

    return (
        <div className={styles.fixAll}>
            {outcome?.error && <Typography variant="caption" className={styles.error} role="alert">{outcome.error}</Typography>}
            <Button label={t('label.fixAll.button')}
                    icon={<Build/>}
                    size="big"
                    color="danger"
                    isLoading={isFixing}
                    isDisabled={errorCount === 0 || isFixing}
                    onClick={() => setConfirming(true)}/>
            <ConfirmDialog isOpen={isConfirming}
                           title={t('label.fixAll.title')}
                           confirmLabel={t('label.fixAll.confirm', {count: errorCount})}
                           icon={<Build/>}
                           isPending={isFixing}
                           onConfirm={fixAll}
                           onClose={() => setConfirming(false)}
                           requiresAcknowledgement
            >
                <Typography>{t('label.fixAll.description', {count: errorCount})}</Typography>
                <Typography className={styles.helper}>{t('label.fixAll.warning')}</Typography>
            </ConfirmDialog>
            <FixOutcomeDialog outcome={outcome?.result} onClose={() => setOutcome(null)}/>
        </div>
    );
};

FixAllAction.propTypes = {
    resultsId: PropTypes.string.isRequired,
    filterArgs: PropTypes.arrayOf(PropTypes.string).isRequired,
    errorCount: PropTypes.number.isRequired,
    onFixed: PropTypes.func.isRequired
};
