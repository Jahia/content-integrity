import React, {useEffect, useLayoutEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Build, Button, Chip, Close, Loader, Typography, Warning} from '@jahia/moonstone';
import {ConfirmDialog} from '../common/ConfirmDialog';
import {JcrBrowserLink} from '../common/JcrBrowserLink';
import {GET_NODE_ERRORS} from '../ContentIntegrity.gql';
import {formatCell} from './columns';
import {FixStatus} from './FixStatus';
import {FixOutcomeDialog} from './FixOutcomeDialog';
import {FixValuesForm, initialFixValues, typedFixValues} from './FixValuesForm';
import {FIX_STATES} from './useFixError';
import styles from '../ContentIntegrity.scss';

const TITLE_ID = 'ci-node-errors-title';
const LINKED = '__linked__';

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
const DetailsList = ({rows}) => {
    const visible = rows.filter(row => !isEmpty(row.value));
    if (visible.length === 0) {
        return null;
    }

    return (
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
    );
};

DetailsList.propTypes = {
    rows: PropTypes.arrayOf(PropTypes.shape({
        label: PropTypes.string.isRequired,
        value: PropTypes.any,
        render: PropTypes.func
    })).isRequired
};

/**
 * The errors of a node, beside the results table, each with its fix. The errors fixed with values are linked when the node
 * has several of them: a node is saved only once all its mandatory properties are set, so their values are saved at once,
 * with a single Fix button, enabled once each of them has a value.
 * The panel is not modal: a click on another row displays the errors of its node. Escape closes it, unless a dialog or a
 * menu is open above the page.
 */
export const NodeErrorsPanel = ({workspace, path, focusedErrorId, resultsId, fixStates, canFixErrors, onFix, onClose}) => {
    const {t, i18n} = useTranslation('content-integrity');
    const panelRef = useRef(null);
    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;
    const {data, loading, error, refetch} = useQuery(GET_NODE_ERRORS, {
        variables: {resultsID: resultsId, workspace, path},
        fetchPolicy: 'no-cache'
    });
    const errors = data?.integrity?.results?.errors || [];
    const node = errors[0];
    // The values typed for each error, by error ID: an error without typed values starts from the default ones
    const [typedValues, setTypedValues] = useState({});
    // The reason of a refused fix, by error ID, or under LINKED for the linked errors
    const [messages, setMessages] = useState({});
    // The outcome of Fix all, displayed in a dialog
    const [outcome, setOutcome] = useState(null);
    // The fix waiting for its confirmation: the number of errors it fixes, and the action of its button
    const [confirmation, setConfirmation] = useState(null);
    const valuesOf = e => typedValues[e.id] || initialFixValues(e.fixValues);

    const isFixed = e => e.fixed || fixStates[e.id] === FIX_STATES.FIXED;
    // Without the permission to fix the errors, the fix status is displayed but no fix is offered
    const isOffered = e => canFixErrors && e.fixable && !isFixed(e);
    const takesValues = e => isOffered(e) && e.fixWithValues && Boolean(e.fixValues);
    const fixOf = e => ({id: e.id, values: takesValues(e) ? typedFixValues(valuesOf(e)) : null});
    // An error fixed with values is ready once it has some
    const isReady = fixes => fixes.length > 0 && fixes.every(f => f.values === null || f.values.length > 0);
    // Fixed one by one, an error fixed with values would be refused while another one still blocks the save of the node
    const valueErrors = errors.filter(takesValues);
    const linked = valueErrors.length > 1 ? valueErrors : [];
    const isLinked = e => linked.some(l => l.id === e.id);
    const isFixing = errors.some(e => fixStates[e.id] === FIX_STATES.FIXING);

    const fix = (key, fixes) => {
        if (!isReady(fixes) || isFixing) {
            return;
        }

        setMessages(prev => ({...prev, [key]: null}));
        onFix(fixes).then(result => {
            refetch();
            if (!result.fixed) {
                // A rejected value is told beside its fix, to be corrected
                setMessages(prev => ({...prev, [key]: result.message || t('label.fix.failedHelper')}));
            }
        });
    };

    const fixLinked = () => fix(LINKED, linked.map(fixOf));
    // Fix all fixes the errors of the node which can be fixed now: an error fixed with values once they are typed, the linked
    // errors once each of them has a value
    const offered = errors.filter(isOffered);
    const isLinkedReady = linked.length > 0 && isReady(linked.map(fixOf));
    const allFixes = offered
        .filter(e => !takesValues(e) || (isLinked(e) ? isLinkedReady : typedFixValues(valuesOf(e)).length > 0))
        .map(fixOf);
    // Fix all tells in a dialog how many errors of the node are fixed, and why the other ones are skipped or not fixed
    const skipReason = e => {
        if (isFixed(e)) {
            return 'ALREADY_FIXED';
        }

        if (!e.fixable) {
            if (e.virtualNode) {
                return 'VIRTUAL_NODE';
            }

            return e.nodeExists ? 'NO_FIX' : 'NODE_NOT_FOUND';
        }

        return allFixes.some(f => f.id === e.id) ? null : 'VALUES_NOT_TYPED';
    };
    const fixAll = () => {
        if (!isReady(allFixes) || isFixing) {
            return;
        }

        const skipped = {};
        errors.map(skipReason).filter(Boolean).forEach(reason => {
            skipped[reason] = (skipped[reason] || 0) + 1;
        });
        const attempted = errors.filter(e => allFixes.some(f => f.id === e.id));
        onFix(allFixes).then(result => {
            refetch();
            const outcomes = Object.fromEntries((result.errors || []).map(r => [r.id, r]));
            const failures = {};
            attempted.filter(e => !outcomes[e.id]?.fixed).forEach(e => {
                // A refused value stops the fix of all the errors: its reason is the one of each error
                const message = outcomes[e.id]?.fixFailureMessage || (result.errors ? null : result.message) || null;
                const key = [e.checkName, e.errorType, message].join('|');
                failures[key] = failures[key] || {checkName: e.checkName, errorType: e.errorType, message, count: 0};
                failures[key].count++;
            });
            setOutcome({
                fixed: attempted.filter(e => outcomes[e.id]?.fixed).length,
                skipped: Object.entries(skipped).map(([reason, count]) => ({reason, count})),
                failures: Object.values(failures)
            });
        });
    };
    // What the fix of an error changes, described by error type: a check without description, such as one from another
    // module, only names itself in the tooltip of its Fix button
    const fixDescriptionKey = e => `label.fixDescription.${e.checkName}.${e.errorType}`;
    const fixDescription = e => (i18n.exists(fixDescriptionKey(e), {ns: 'content-integrity'}) ? t(fixDescriptionKey(e)) : null);
    // An error which depends on a resource of the server which ran the scan, such as its search index or its started modules,
    // tells what it means in a cluster, where each server has its own: described by error type, as the fixes
    const clusterHelperKey = e => `label.cluster.${e.checkName}.${e.errorType}`;
    const clusterHelper = e => (i18n.exists(clusterHelperKey(e), {ns: 'content-integrity'}) ? t(clusterHelperKey(e)) : null);
    const fixOne = e => fix(e.id, [fixOf(e)]);
    // A fix writes to the repository: its button is a danger one, light for the fix of an error, full for Fix all. It runs
    // once confirmed, whether it is asked for by its button or by Enter in a field of its values
    const askConfirmation = (fixes, run) => {
        if (isReady(fixes) && !isFixing) {
            setConfirmation({count: fixes.length, run});
        }
    };
    const confirmFix = () => {
        const {run} = confirmation;
        setConfirmation(null);
        run();
    };
    const fixButton = (fixes, onClick, label, title, isFixAll = false) => (
        <Button className={styles.fixButton}
                label={label}
                icon={<Build/>}
                size="big"
                color="danger"
                variant={isFixAll ? 'default' : 'outlined'}
                isLoading={fixes.some(f => fixStates[f.id] === FIX_STATES.FIXING)}
                isDisabled={!isReady(fixes) || isFixing}
                title={title}
                onClick={() => askConfirmation(fixes, onClick)}/>
    );

    // The panel starts below the header of the page, whose actions, such as Refresh, stay usable while it is open
    const [top, setTop] = useState(0);
    useLayoutEffect(() => {
        const measure = () => setTop(Math.max(0, document.querySelector('[data-ci-page-content]')?.getBoundingClientRect().top || 0));
        measure();
        window.addEventListener('resize', measure);
        return () => window.removeEventListener('resize', measure);
    }, []);

    // The focus goes to the panel when it opens, so a keyboard user reads it next
    useEffect(() => {
        panelRef.current?.focus();
    }, [workspace, path]);

    // The error of the clicked row is scrolled into view
    useEffect(() => {
        if (focusedErrorId && !loading) {
            document.getElementById(`ci-node-error-${focusedErrorId}`)?.scrollIntoView?.({block: 'nearest'});
        }
    }, [focusedErrorId, loading]);

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

    const nodeLink = value => <JcrBrowserLink uuid={node.nodeId} workspace={node.workspace}>{value}</JcrBrowserLink>;
    const label = key => t(`label.column.${key}`);

    return (
        <aside ref={panelRef} id="ci-node-errors" className={styles.sidePanel} style={{top}} tabIndex={-1} aria-labelledby={TITLE_ID}>
            <div className={styles.sidePanelHeader}>
                <Typography id={TITLE_ID} variant="heading" weight="bold" component="h2">
                    {t('label.details.title')}
                </Typography>
                <Button variant="ghost" icon={<Close/>} title={t('label.close')} aria-label={t('label.close')} onClick={onClose}/>
            </div>
            <div className={styles.sidePanelBody}>
                {loading && !data && <div className={styles.centered}><Loader size="big"/></div>}
                {error && <Typography className={styles.error}>{error.message}</Typography>}
                {!loading && !error && !node && <Typography>{t('label.details.unknown')}</Typography>}
                {node && (
                    <div className={styles.details}>
                        <div className={styles.detailsSummary}>
                            <Typography variant="heading" weight="bold" className={`${styles.detailsMessage} ${styles.detailsValue}`}>
                                {nodeLink(withPathBreaks(node.nodePath))}
                            </Typography>
                            {linked.length > 0 && (
                                <>
                                    <Typography variant="body" className={styles.helper}>{t('label.fix.linkedHelper')}</Typography>
                                    <div className={styles.fixActionRow}>
                                        {fixButton(linked.map(fixOf), fixLinked, t('label.fix.fixCount', {count: linked.length}), t('label.fix.nodeHelper'))}
                                    </div>
                                </>
                            )}
                            {messages[LINKED] && <Typography className={styles.error} role="alert">{messages[LINKED]}</Typography>}
                        </div>
                        <DetailsList rows={[
                            {label: label('nodeId'), value: node.nodeId, render: nodeLink},
                            {label: label('nodePrimaryType'), value: node.nodePrimaryType},
                            {label: label('nodeMixins'), value: node.nodeMixins},
                            {label: label('workspace'), value: node.workspace},
                            {label: label('site'), value: node.site}
                        ]}/>
                        <section className={styles.nodeErrors} aria-label={t('label.details.errors')}>
                            {errors.map(e => (
                                <article key={e.id}
                                         id={`ci-node-error-${e.id}`}
                                         data-error-id={e.id}
                                         className={`${styles.nodeError} ${e.id === focusedErrorId ? styles.nodeErrorFocused : ''}`}
                                >
                                    <Typography variant="subheading" weight="bold" className={styles.detailsMessage}>{e.message}</Typography>
                                    <div className={styles.detailsChips}>
                                        <FixStatus error={e} state={fixStates[e.id]}/>
                                        {/* An error without fix may have one, but no node left to fix: the node has been removed since the scan */}
                                        {!isFixed(e) && !e.fixable && <Chip label={t(e.nodeExists ? 'label.details.noFix' : 'label.details.nodeRemoved')} color="warning"/>}
                                        {e.importError === true && <Chip label={t('label.details.impactsImport')} icon={<Warning/>} color="danger"/>}
                                    </div>
                                    {!isFixed(e) && clusterHelper(e) && (
                                        <div className={styles.clusterHelper} data-cluster-helper>
                                            <Typography weight="bold">{t('label.cluster.title')}</Typography>
                                            <Typography className={styles.helper}>{clusterHelper(e)}</Typography>
                                        </div>
                                    )}
                                    {e.virtualNode && !isFixed(e) && <Typography className={styles.helper}>{t('label.details.virtualNode')}</Typography>}
                                    {!e.nodeExists && !isFixed(e) && <Typography className={styles.helper} data-node-removed>{t('label.details.nodeRemovedHelper')}</Typography>}
                                    {(fixStates[e.id] === FIX_STATES.FAILED || (e.fixFailed && !fixStates[e.id])) && <Typography className={styles.helper}>{t('label.fix.failedHelper')}</Typography>}
                                    {isOffered(e) && fixDescription(e) && <div className={styles.fixDescription} data-fix-description><Typography>{fixDescription(e)}</Typography></div>}
                                    {takesValues(e) && (
                                        <FixValuesForm errorId={e.id}
                                                       definition={e.fixValues}
                                                       values={valuesOf(e)}
                                                       message={isLinked(e) ? null : messages[e.id]}
                                                       isDisabled={isFixing}
                                                       onChange={next => {
                                                           setTypedValues(prev => ({...prev, [e.id]: next}));
                                                           setMessages(prev => ({...prev, [isLinked(e) ? LINKED : e.id]: null}));
                                                       }}
                                                       onSubmit={() => (isLinked(e) ? askConfirmation(linked.map(fixOf), fixLinked) : askConfirmation([fixOf(e)], () => fixOne(e)))}
                                        >
                                            {!isLinked(e) && fixButton([fixOf(e)], () => fixOne(e), t('label.fix.fix'), t('label.fix.withValuesHelper'))}
                                        </FixValuesForm>
                                    )}
                                    {isOffered(e) && !takesValues(e) && (
                                        <>
                                            <div className={styles.fixActionRow}>
                                                {fixButton([fixOf(e)], () => fixOne(e), t('label.fix.fix'), t('label.fix.helper', {check: e.checkName}))}
                                            </div>
                                            {messages[e.id] && <Typography className={styles.error} role="alert">{messages[e.id]}</Typography>}
                                        </>
                                    )}
                                    <DetailsList rows={[
                                        {label: label('checkName'), value: e.checkName},
                                        {label: label('errorType'), value: e.errorType},
                                        {label: label('locale'), value: e.locale},
                                        ...(e.extraInfos || []).map(info => ({label: toLabel(info.label), value: info.value}))
                                    ]}/>
                                </article>
                            ))}
                        </section>
                    </div>
                )}
            </div>
            {canFixErrors && errors.length > 1 && (
                // The errors of a node with several errors are fixed at once from the bottom of the panel: disabled when none can be
                // fixed now, for example when none has a fix or when their values are not typed yet
                <div className={styles.sidePanelFooter}>
                    <div className={styles.fixActionRow}>
                        {fixButton(allFixes, fixAll, t('label.fix.fixAllNode', {count: allFixes.length}), t('label.fix.fixAllNodeHelper'), true)}
                    </div>
                </div>
            )}
            <ConfirmDialog isOpen={confirmation !== null}
                           title={t('label.fix.confirmTitle')}
                           confirmLabel={t('label.fix.fixCount', {count: confirmation?.count || 0})}
                           icon={<Build/>}
                           onConfirm={confirmFix}
                           onClose={() => setConfirmation(null)}
                           requiresAcknowledgement
            >
                <Typography>{t('label.fix.confirmDescription', {count: confirmation?.count || 0, path})}</Typography>
                <Typography className={styles.helper}>{t('label.fix.confirmWarning')}</Typography>
            </ConfirmDialog>
            <FixOutcomeDialog outcome={outcome} onClose={() => setOutcome(null)}/>
        </aside>
    );
};

NodeErrorsPanel.propTypes = {
    workspace: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired,
    focusedErrorId: PropTypes.string,
    resultsId: PropTypes.string.isRequired,
    fixStates: PropTypes.object.isRequired,
    canFixErrors: PropTypes.bool,
    onFix: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
