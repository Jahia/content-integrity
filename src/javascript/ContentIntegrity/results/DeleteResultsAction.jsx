import React, {useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Delete, Menu, MenuItem, MoreVert, Typography} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import {DELETE_ALL_SCAN_RESULTS, DELETE_SCAN_RESULTS} from '../ContentIntegrity.gql';
import styles from '../ContentIntegrity.scss';

/**
 * A deletion of stored results, which can't be undone, so the administrator confirms first. trigger renders what opens
 * the confirmation, from the function it receives. onDeleted is called once the results are deleted: the page then
 * displays no results until a scan is chosen, as after a refresh.
 */
const ConfirmedDeletion = ({trigger, labels, query, variables, onDeleted}) => {
    const {t} = useTranslation('content-integrity');
    const client = useApolloClient();
    const [isConfirming, setConfirming] = useState(false);
    const [isDeleting, setDeleting] = useState(false);
    const [error, setError] = useState(null);

    const deleteResults = () => {
        setDeleting(true);
        setError(null);
        client.query({query, variables, fetchPolicy: 'no-cache'})
            .then(() => {
                setConfirming(false);
                onDeleted();
            })
            .catch(e => setError(e.message))
            .finally(() => setDeleting(false));
    };

    return (
        <>
            {trigger(() => {
                setError(null);
                setConfirming(true);
            })}
            <Dialog isOpen={isConfirming}
                    title={labels.title}
                    actions={(
                        <>
                            <Button label={t('label.cancel')} variant="outlined" isDisabled={isDeleting} onClick={() => setConfirming(false)}/>
                            <Button label={labels.confirm}
                                    icon={<Delete/>}
                                    color="danger"
                                    isLoading={isDeleting}
                                    isDisabled={isDeleting}
                                    onClick={deleteResults}/>
                        </>
                    )}
                    onClose={() => !isDeleting && setConfirming(false)}
            >
                <Typography>{labels.description}</Typography>
                <Typography className={styles.helper}>{t('label.deleteResults.warning')}</Typography>
                {error && <Typography variant="caption" className={styles.error} role="alert">{error}</Typography>}
            </Dialog>
        </>
    );
};

ConfirmedDeletion.propTypes = {
    trigger: PropTypes.func.isRequired,
    labels: PropTypes.shape({
        title: PropTypes.string.isRequired,
        description: PropTypes.string.isRequired,
        confirm: PropTypes.string.isRequired
    }).isRequired,
    query: PropTypes.object.isRequired,
    variables: PropTypes.object,
    onDeleted: PropTypes.func.isRequired
};

/**
 * Deletes the stored results of a scan: its errors, its log, and its CSV and XLSX reports. It is displayed as the links
 * of the reports.
 */
export const DeleteResultsAction = ({resultsId, scanLabel, onDeleted}) => {
    const {t} = useTranslation('content-integrity');
    return (
        <ConfirmedDeletion trigger={open => (
                               <button id="ci-delete-results"
                                       type="button"
                                       className={`${styles.reportLink} ${styles.reportAction}`}
                                       onClick={open}
                               >
                                   <Delete/>
                                   <Typography variant="body" component="span">{t('label.deleteResults.button')}</Typography>
                               </button>
                           )}
                           labels={{
                               title: t('label.deleteResults.title'),
                               description: t('label.deleteResults.description', {scan: scanLabel}),
                               confirm: t('label.deleteResults.confirm')
                           }}
                           query={DELETE_SCAN_RESULTS}
                           variables={{id: resultsId}}
                           onDeleted={onDeleted}/>
    );
};

DeleteResultsAction.propTypes = {
    resultsId: PropTypes.string.isRequired,
    scanLabel: PropTypes.string.isRequired,
    onDeleted: PropTypes.func.isRequired
};

/**
 * The menu of the actions on all the stored results, displayed after the title of the results card. Its only action
 * deletes the stored results of all the scans, except the ones of a running scan.
 */
export const ResultsMenu = ({count, onDeleted}) => {
    const {t} = useTranslation('content-integrity');
    const anchor = useRef(null);
    const [isOpen, setOpen] = useState(false);
    return (
        <ConfirmedDeletion trigger={open => (
                               <>
                                   <div ref={anchor} className={styles.resultsMenu}>
                                       <Button id="ci-results-menu"
                                               icon={<MoreVert/>}
                                               variant="ghost"
                                               aria-label={t('label.results.actions')}
                                               title={t('label.results.actions')}
                                               aria-haspopup="menu"
                                               aria-expanded={isOpen}
                                               onClick={() => setOpen(true)}/>
                                   </div>
                                   {/* Mounted only while open: a closed menu would leave its items in the page */}
                                   {isOpen && (
                                       <Menu isDisplayed
                                             anchorEl={anchor}
                                             anchorElOrigin={{vertical: 'bottom', horizontal: 'left'}}
                                             onClose={() => setOpen(false)}
                                       >
                                           <MenuItem id="ci-delete-all-results"
                                                     className={styles.dangerMenuItem}
                                                     label={t('label.deleteAllResults.button')}
                                                     iconStart={<Delete/>}
                                                     onClick={() => {
                                                         setOpen(false);
                                                         open();
                                                     }}/>
                                       </Menu>
                                   )}
                               </>
                           )}
                           labels={{
                               title: t('label.deleteAllResults.title'),
                               description: t('label.deleteAllResults.description', {count}),
                               confirm: t('label.deleteAllResults.confirm', {count})
                           }}
                           query={DELETE_ALL_SCAN_RESULTS}
                           onDeleted={onDeleted}/>
    );
};

ResultsMenu.propTypes = {
    count: PropTypes.number.isRequired,
    onDeleted: PropTypes.func.isRequired
};
