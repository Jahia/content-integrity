import React from 'react';
import PropTypes from 'prop-types';
import {useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Chip, Loader, Typography, Warning} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import {JcrBrowserLink} from '../common/JcrBrowserLink';
import {GET_ERROR_DETAILS} from '../ContentIntegrity.gql';
import {formatCell} from './columns';
import {FixAction} from './FixAction';
import {FixValuesForm} from './FixValuesForm';
import {FIX_STATES} from './useFixError';
import styles from '../ContentIntegrity.scss';

const isEmpty = value => value === null || value === undefined || value === '';

// A labelled list that leaves out the empty values, so the reader only sees what the check reported.
const DetailsSection = ({title, rows}) => {
    const visible = rows.filter(row => !isEmpty(row.value));
    if (visible.length === 0) {
        return null;
    }

    return (
        <section className={styles.detailsSection}>
            <Typography variant="subheading" weight="bold" className={styles.detailsSectionTitle}>{title}</Typography>
            <dl className={styles.detailsList}>
                {visible.map(row => (
                    <React.Fragment key={row.label}>
                        <dt><Typography variant="caption" component="span" className={styles.detailsLabel}>{row.label}</Typography></dt>
                        <dd>
                            <Typography variant="body" component="span" className={row.isCode ? `${styles.breakAll} ${styles.code}` : styles.breakAll}>
                                {row.render ? row.render(row.value) : formatCell(row.value)}
                            </Typography>
                        </dd>
                    </React.Fragment>
                ))}
            </dl>
        </section>
    );
};

DetailsSection.propTypes = {
    title: PropTypes.string.isRequired,
    rows: PropTypes.arrayOf(PropTypes.shape({
        label: PropTypes.string.isRequired,
        value: PropTypes.any,
        isCode: PropTypes.bool,
        render: PropTypes.func
    })).isRequired
};

export const ErrorDetailsDialog = ({errorId, resultsId, fixState, onFix, onClose}) => {
    const {t} = useTranslation('content-integrity');
    const {data, loading, error} = useQuery(GET_ERROR_DETAILS, {
        variables: {id: errorId, resultsID: resultsId},
        fetchPolicy: 'no-cache'
    });
    const details = data?.integrity?.results?.error;

    const nodeLink = value => <JcrBrowserLink uuid={details.nodeId} workspace={details.workspace}>{value}</JcrBrowserLink>;
    const label = key => t(`label.column.${key}`);

    return (
        <Dialog isOpen
                title={t('label.details.title')}
                size="large"
                actions={(
                    <>
                        {details && (!details.fixWithValues || fixState === FIX_STATES.FIXED) && <FixAction error={details} state={fixState} onFix={onFix}/>}
                        <Button label={t('label.close')} variant="outlined" onClick={onClose}/>
                    </>
                )}
                onClose={onClose}
        >
            {loading && <div className={styles.centered}><Loader size="big"/></div>}
            {error && <Typography className={styles.error}>{error.message}</Typography>}
            {!loading && !error && !details && <Typography>{t('label.details.unknown')}</Typography>}
            {details && (
                <div className={styles.details}>
                    <div className={styles.detailsSummary}>
                        <Typography variant="heading" weight="bold">{details.message}</Typography>
                        {(details.importError === true || details.fixed || fixState === FIX_STATES.FIXED) && (
                            <div className={styles.detailsChips}>
                                {details.importError === true && <Chip label={t('label.details.impactsImport')} icon={<Warning/>} color="warning"/>}
                                {(details.fixed || fixState === FIX_STATES.FIXED) && <Chip label={t('label.fix.fixed')} color="success"/>}
                            </div>
                        )}
                    </div>
                    <DetailsSection title={t('label.details.node')}
                                    rows={[
                                        {label: label('nodePath'), value: details.nodePath, isCode: true, render: nodeLink},
                                        {label: label('nodeId'), value: details.nodeId, isCode: true, render: nodeLink},
                                        {label: label('nodePrimaryType'), value: details.nodePrimaryType, isCode: true},
                                        {label: label('nodeMixins'), value: details.nodeMixins, isCode: true},
                                        {label: label('workspace'), value: details.workspace},
                                        {label: label('site'), value: details.site},
                                        {label: label('locale'), value: details.locale}
                                    ]}/>
                    <DetailsSection title={t('label.details.check')}
                                    rows={[
                                        {label: label('checkName'), value: details.checkName},
                                        {label: label('errorType'), value: details.errorType, isCode: true},
                                        {label: label('importError'), value: details.importError === null ? null : t(details.importError ? 'label.details.yes' : 'label.details.no')}
                                    ]}/>
                    <DetailsSection title={t('label.details.extraInfos')}
                                    rows={(details.extraInfos || []).map(info => ({label: info.label, value: info.value, isCode: true}))}/>
                    {details.fixWithValues && details.fixValues && fixState !== FIX_STATES.FIXED && (
                        <FixValuesForm errorId={details.id}
                                       definition={details.fixValues}
                                       isFixing={fixState === FIX_STATES.FIXING}
                                       onFix={onFix}/>
                    )}
                </div>
            )}
        </Dialog>
    );
};

ErrorDetailsDialog.propTypes = {
    errorId: PropTypes.string.isRequired,
    resultsId: PropTypes.string.isRequired,
    fixState: PropTypes.string,
    onFix: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
