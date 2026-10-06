import React, {useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Checkbox, Dropdown, Field, Input, Loader, Typography} from '@jahia/moonstone';
import {GET_CHECKS} from '../ContentIntegrity.gql';
import {Dialog} from '../common/Dialog';
import {ChecksSelector} from './ChecksSelector';
import {ExcludedPaths} from './ExcludedPaths';
import styles from '../ContentIntegrity.scss';

const WORKSPACES = ['EDIT', 'LIVE', 'BOTH'];

/**
 * Configures and starts a scan. The component stays mounted while the dialog is closed, so the
 * selection and the parameters are kept from one scan to the next.
 */
export const NewScanDialog = ({isOpen, onClose, onRun}) => {
    const {t} = useTranslation('content-integrity');
    const {data, loading, error} = useQuery(GET_CHECKS, {fetchPolicy: 'network-only'});

    const [selected, setSelected] = useState(null);
    const [rootPath, setRootPath] = useState('/');
    const [excludedPaths, setExcludedPaths] = useState([]);
    const [workspace, setWorkspace] = useState('EDIT');
    const [includeVirtualNodes, setIncludeVirtualNodes] = useState(true);

    // Sorted alphabetically on their identifier, which is not translated: the order is the same in every UI language.
    const checks = useMemo(() => [...(data?.integrity?.checks || [])]
        .sort((a, b) => a.id.localeCompare(b.id, 'en', {sensitivity: 'base'})), [data]);

    // The checks configured as active are preselected.
    useEffect(() => {
        if (selected === null && data) {
            setSelected(checks.filter(c => c.enabled).map(c => c.id));
        }
    }, [data, checks, selected]);

    const workspaceData = WORKSPACES.map(value => ({value, label: t(`label.scan.workspace.${value}`)}));
    const canRun = !loading && !error && selected?.length > 0 && rootPath.trim().length > 0;

    return (
        <Dialog isOpen={isOpen}
                title={t('label.scan.newScanTitle')}
                size="xlarge"
                actions={(
                    <>
                        <Button label={t('label.cancel')} variant="outlined" onClick={onClose}/>
                        <Button label={t('label.scan.run')}
                                color="accent"
                                isDisabled={!canRun}
                                onClick={() => onRun({
                                    rootPath: rootPath.trim(),
                                    excludedPaths,
                                    workspace,
                                    includeVirtualNodes,
                                    checks: selected
                                })}/>
                    </>
                )}
                onClose={onClose}
        >
            {loading && <div className={styles.centered}><Loader size="big"/></div>}
            {error && <Typography className={styles.error}>{error.message}</Typography>}
            {data && (
                <div className={styles.dialogSections}>
                    <section className={styles.section} aria-labelledby="ci-params-title">
                        <Typography id="ci-params-title" variant="subheading" weight="bold" className={styles.sectionTitle}>
                            {t('label.scan.parameters')}
                        </Typography>
                        <div className={styles.formGrid}>
                            <Field id="ci-root-node-field" label={t('label.scan.rootNode')} helper={t('label.scan.rootNodeHelper')}>
                                <Input id="ci-root-node"
                                       aria-label={t('label.scan.rootNode')}
                                       value={rootPath}
                                       onChange={e => setRootPath(e.target.value)}/>
                            </Field>
                            <Field id="ci-workspace-field" label={t('label.scan.workspace.label')}>
                                <Dropdown data={workspaceData}
                                          value={workspace}
                                          variant="outlined"
                                          onChange={(e, item) => setWorkspace(item.value)}/>
                            </Field>
                            <ExcludedPaths paths={excludedPaths} onChange={setExcludedPaths}/>
                            <div className={styles.inlineField}>
                                <label className={styles.checkboxLabel} htmlFor="ci-virtual-nodes">
                                    <Checkbox id="ci-virtual-nodes"
                                              checked={includeVirtualNodes}
                                              onChange={(e, v, checked) => setIncludeVirtualNodes(checked)}/>
                                    <Typography variant="body" component="span">{t('label.scan.includeVirtualNodes')}</Typography>
                                </label>
                                <Typography variant="caption" className={styles.helper}>{t('label.scan.includeVirtualNodesHelper')}</Typography>
                            </div>
                        </div>
                    </section>
                    <ChecksSelector checks={checks} selected={selected || []} onChange={setSelected}/>
                </div>
            )}
        </Dialog>
    );
};

NewScanDialog.propTypes = {
    isOpen: PropTypes.bool.isRequired,
    onClose: PropTypes.func.isRequired,
    onRun: PropTypes.func.isRequired
};
