import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Delete, Typography} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import {DELETE_SCAN_RESULTS} from '../ContentIntegrity.gql';
import styles from '../ContentIntegrity.scss';

/**
 * Deletes the stored results of a scan: its errors, its log, and its CSV and XLSX reports. The deletion can't be undone,
 * so the administrator confirms first. It is displayed after the links of the reports, as one of them. onDeleted is
 * called once the results are deleted, to read the list of the results again.
 */
export const DeleteResultsAction = ({resultsId, scanLabel, onDeleted}) => {
    const {t} = useTranslation('content-integrity');
    const client = useApolloClient();
    const [isConfirming, setConfirming] = useState(false);
    const [isDeleting, setDeleting] = useState(false);
    const [error, setError] = useState(null);

    const deleteResults = () => {
        setDeleting(true);
        setError(null);
        client.query({query: DELETE_SCAN_RESULTS, variables: {id: resultsId}, fetchPolicy: 'no-cache'})
            .then(() => {
                setConfirming(false);
                onDeleted(resultsId);
            })
            .catch(e => setError(e.message))
            .finally(() => setDeleting(false));
    };

    return (
        <>
            <button id="ci-delete-results"
                    type="button"
                    className={`${styles.reportLink} ${styles.reportAction}`}
                    onClick={() => {
                        setError(null);
                        setConfirming(true);
                    }}
            >
                <Delete/>
                <Typography variant="body" component="span">{t('label.deleteResults.button')}</Typography>
            </button>
            <Dialog isOpen={isConfirming}
                    title={t('label.deleteResults.title')}
                    actions={(
                        <>
                            <Button label={t('label.cancel')} variant="outlined" isDisabled={isDeleting} onClick={() => setConfirming(false)}/>
                            <Button label={t('label.deleteResults.confirm')}
                                    icon={<Delete/>}
                                    color="danger"
                                    isLoading={isDeleting}
                                    isDisabled={isDeleting}
                                    onClick={deleteResults}/>
                        </>
                    )}
                    onClose={() => !isDeleting && setConfirming(false)}
            >
                <Typography>{t('label.deleteResults.description', {scan: scanLabel})}</Typography>
                <Typography className={styles.helper}>{t('label.deleteResults.warning')}</Typography>
                {error && <Typography variant="caption" className={styles.error} role="alert">{error}</Typography>}
            </Dialog>
        </>
    );
};

DeleteResultsAction.propTypes = {
    resultsId: PropTypes.string.isRequired,
    scanLabel: PropTypes.string.isRequired,
    onDeleted: PropTypes.func.isRequired
};
