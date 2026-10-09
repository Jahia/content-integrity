import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Chip, Close, Loader, Typography, Warning} from '@jahia/moonstone';
import {JcrBrowserLink} from '../common/JcrBrowserLink';
import {GET_ERROR_DETAILS} from '../ContentIntegrity.gql';
import {formatCell} from './columns';
import {FixAction} from './FixAction';
import {FixStatus} from './FixStatus';
import {FixValuesForm, initialFixValues, typedFixValues} from './FixValuesForm';
import {FIX_STATES} from './useFixError';
import styles from '../ContentIntegrity.scss';

const TITLE_ID = 'ci-error-details-title';

const isEmpty = value => value === null || value === undefined || value === '';

// A long path wraps after a slash, never in the middle of a node name
const withPathBreaks = path => path.split('/').map((part, i) => (
    <React.Fragment key={i}>{i > 0 && <>/<wbr/></>}{part}</React.Fragment>
));

// The checks name their additional information with technical keys, such as property-name
const toLabel = key => {
    const words = String(key).replace(/[-_]+/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2').trim().toLowerCase();
    return words.charAt(0).toUpperCase() + words.slice(1);
};

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
                        <dt><Typography variant="body" component="span" className={styles.detailsLabel}>{row.label}</Typography></dt>
                        <dd>
                            <Typography variant="body" component="span" className={styles.detailsValue}>
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
        render: PropTypes.func
    })).isRequired
};

/**
 * The details of the error of the selected row, beside the results table, with its fix. The panel is not modal: a click
 * on another row displays the details of its error. Escape closes it, unless a dialog or a menu is open above the page.
 */
export const ErrorDetailsPanel = ({errorId, resultsId, fixState, canFixErrors, onFix, onClose}) => {
    const {t} = useTranslation('content-integrity');
    const panelRef = useRef(null);
    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;
    const {data, loading, error} = useQuery(GET_ERROR_DETAILS, {
        variables: {id: errorId, resultsID: resultsId},
        fetchPolicy: 'no-cache'
    });
    const details = data?.integrity?.results?.error;
    // Without the permission to fix the errors, the fix status is displayed but no fix is offered
    const fixableError = details && {...details, fixable: canFixErrors && details.fixable};
    const isFixed = details && (details.fixed || fixState === FIX_STATES.FIXED);
    // An error fixed with values takes the values typed in the details: the Fix button sends them
    const takesValues = Boolean(canFixErrors && details?.fixWithValues && details.fixValues);
    const [valuesState, setValuesState] = useState({errorId: null, values: [''], message: null});
    const values = valuesState.errorId === details?.id ? valuesState.values : initialFixValues(details?.fixValues);
    const valuesMessage = valuesState.errorId === details?.id ? valuesState.message : null;
    const isFixing = fixState === FIX_STATES.FIXING;
    const fix = () => {
        if (!takesValues) {
            onFix(details.id);
            return;
        }

        const typed = typedFixValues(values);
        if (typed.length === 0 || isFixing) {
            return;
        }

        const id = details.id;
        setValuesState({errorId: id, values, message: null});
        onFix(id, typed).then(result => {
            if (!result.fixed) {
                // A rejected value is shown on its field, to be corrected
                setValuesState(prev => (prev.errorId === id ? {...prev, message: result.message || t('label.fix.failedHelper')} : prev));
            }
        });
    };

    // The focus goes to the panel when it opens and when it displays another error, so a keyboard user reads it next
    useEffect(() => {
        panelRef.current?.focus();
    }, [errorId]);

    useEffect(() => {
        const onKeyDown = event => {
            if (event.key !== 'Escape' || document.querySelector('[role=dialog], .moonstone-menu_overlay')) {
                return;
            }

            onCloseRef.current();
        };

        document.addEventListener('keydown', onKeyDown);
        return () => document.removeEventListener('keydown', onKeyDown);
    }, []);

    // The fix comes right after the summary of the error: in the form of the values when the error takes some
    const fixAction = fixableError?.fixable && !isFixed && (
        <FixAction error={fixableError}
                   state={fixState}
                   isDisabled={takesValues && typedFixValues(values).length === 0}
                   onClick={fix}/>
    );

    const nodeLink = value => <JcrBrowserLink uuid={details.nodeId} workspace={details.workspace}>{value}</JcrBrowserLink>;
    const label = key => t(`label.column.${key}`);

    return (
        <aside ref={panelRef} id="ci-error-details" className={styles.sidePanel} tabIndex={-1} aria-labelledby={TITLE_ID}>
            <div className={styles.sidePanelHeader}>
                <Typography id={TITLE_ID} variant="heading" weight="bold" component="h2">
                    {t('label.details.title')}
                </Typography>
                <Button variant="ghost" icon={<Close/>} title={t('label.close')} aria-label={t('label.close')} onClick={onClose}/>
            </div>
            <div className={styles.sidePanelBody}>
                {loading && <div className={styles.centered}><Loader size="big"/></div>}
                {error && <Typography className={styles.error}>{error.message}</Typography>}
                {!loading && !error && !details && <Typography>{t('label.details.unknown')}</Typography>}
                {details && (
                    <div className={styles.details}>
                        <div className={styles.detailsSummary}>
                            <Typography variant="heading" weight="bold" className={styles.detailsMessage}>{details.message}</Typography>
                            <div className={styles.detailsChips}>
                                <FixStatus error={details} state={fixState}/>
                                {details.importError === true && <Chip label={t('label.details.impactsImport')} icon={<Warning/>} color="warning"/>}
                            </div>
                            {details.virtualNode && !isFixed && <Typography className={styles.helper}>{t('label.details.virtualNode')}</Typography>}
                            {fixState === FIX_STATES.FAILED && <Typography className={styles.helper}>{t('label.fix.failedHelper')}</Typography>}
                        </div>
                        {takesValues && !isFixed && (
                            <FixValuesForm errorId={details.id}
                                           definition={details.fixValues}
                                           values={values}
                                           message={valuesMessage}
                                           isDisabled={isFixing}
                                           onChange={next => setValuesState({errorId: details.id, values: next, message: null})}
                                           onSubmit={fix}
                            >
                                {fixAction}
                            </FixValuesForm>
                        )}
                        {!takesValues && fixAction && <div className={styles.fixActionRow}>{fixAction}</div>}
                        <DetailsSection title={t('label.details.node')}
                                        rows={[
                                            {label: label('nodePath'), value: details.nodePath, render: value => nodeLink(withPathBreaks(value))},
                                            {label: label('nodeId'), value: details.nodeId, render: nodeLink},
                                            {label: label('nodePrimaryType'), value: details.nodePrimaryType},
                                            {label: label('nodeMixins'), value: details.nodeMixins},
                                            {label: label('workspace'), value: details.workspace},
                                            {label: label('site'), value: details.site},
                                            {label: label('locale'), value: details.locale}
                                        ]}/>
                        <DetailsSection title={t('label.details.check')}
                                        rows={[
                                            {label: label('checkName'), value: details.checkName},
                                            {label: label('errorType'), value: details.errorType},
                                            {label: label('importError'), value: details.importError === null ? null : t(details.importError ? 'label.details.yes' : 'label.details.no')}
                                        ]}/>
                        <DetailsSection title={t('label.details.extraInfos')}
                                        rows={(details.extraInfos || []).map(info => ({label: toLabel(info.label), value: info.value}))}/>
                    </div>
                )}
            </div>
        </aside>
    );
};

ErrorDetailsPanel.propTypes = {
    errorId: PropTypes.string.isRequired,
    resultsId: PropTypes.string.isRequired,
    fixState: PropTypes.string,
    canFixErrors: PropTypes.bool,
    onFix: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
