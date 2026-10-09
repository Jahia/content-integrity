import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Build, Button, Typography} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import {FIX_ALL_ERRORS} from '../ContentIntegrity.gql';
import styles from '../ContentIntegrity.scss';

/**
 * Fixes all the errors matching the active filters, each with the fix of the check which has detected it. A fix can
 * remove content, so the administrator confirms first. The outcome stays displayed until the next fix or filter change,
 * and is passed to onFixed, which tags the errors not fixed.
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
        client.query({query: FIX_ALL_ERRORS, variables: {resultsID: resultsId, filters: filterArgs}, fetchPolicy: 'no-cache'})
            .then(({data}) => {
                const result = data?.integrity?.results?.fixAll;
                setOutcome({result});
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
            {outcome?.result && (
                <Typography variant="caption" className={styles.helper} role="status">
                    {t('label.fixAll.outcome', outcome.result)}
                </Typography>
            )}
            {outcome?.error && <Typography variant="caption" className={styles.error} role="alert">{outcome.error}</Typography>}
            <Button label={t('label.fixAll.button')}
                    icon={<Build/>}
                    variant="outlined"
                    isLoading={isFixing}
                    isDisabled={errorCount === 0 || isFixing}
                    onClick={() => setConfirming(true)}/>
            <Dialog isOpen={isConfirming}
                    title={t('label.fixAll.title')}
                    actions={(
                        <>
                            <Button label={t('label.cancel')} variant="outlined" isDisabled={isFixing} onClick={() => setConfirming(false)}/>
                            <Button label={t('label.fixAll.confirm', {count: errorCount})}
                                    icon={<Build/>}
                                    color="danger"
                                    isLoading={isFixing}
                                    isDisabled={isFixing}
                                    onClick={fixAll}/>
                        </>
                    )}
                    onClose={() => !isFixing && setConfirming(false)}
            >
                <Typography>{t('label.fixAll.description', {count: errorCount})}</Typography>
                <Typography className={styles.helper}>{t('label.fixAll.warning')}</Typography>
            </Dialog>
        </div>
    );
};

FixAllAction.propTypes = {
    resultsId: PropTypes.string.isRequired,
    filterArgs: PropTypes.arrayOf(PropTypes.string).isRequired,
    errorCount: PropTypes.number.isRequired,
    onFixed: PropTypes.func.isRequired
};
