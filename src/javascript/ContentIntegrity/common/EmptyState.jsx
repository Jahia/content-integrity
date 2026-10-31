import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Add, Button, Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.module.scss';

/**
 * The content of the results card when it displays no results: a title, a hint, and the button to run a new scan, the
 * same whether no scan is stored or none is selected.
 */
export const EmptyState = ({title, hint, isScanLocked, onNewScan}) => {
    const {t} = useTranslation('content-integrity');
    return (
        <div id="ci-empty-state" className={styles.emptyState}>
            <Typography variant="heading">{title}</Typography>
            <Typography variant="body" className={styles.helper}>{hint}</Typography>
            <Button label={t('label.scan.newScan')} icon={<Add/>} color="accent" isDisabled={isScanLocked} onClick={onNewScan}/>
        </div>
    );
};

EmptyState.propTypes = {
    title: PropTypes.string.isRequired,
    hint: PropTypes.string.isRequired,
    isScanLocked: PropTypes.bool,
    onNewScan: PropTypes.func.isRequired
};
