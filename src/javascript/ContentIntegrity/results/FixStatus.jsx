import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Chip, Loader} from '@jahia/moonstone';
import {FIX_STATES} from './useFixError';

/**
 * The outcome of the fix of an error: a loader while it runs, then a tag telling if the error is fixed. It is displayed in
 * the row of the error and in its details, and stays until other results are displayed.
 */
export const FixStatus = ({error, state}) => {
    const {t} = useTranslation('content-integrity');

    if (state === FIX_STATES.FIXING) {
        return <Loader size="small"/>;
    }
    if (error.fixed || state === FIX_STATES.FIXED) {
        return <Chip label={t('label.fix.fixed')} color="success"/>;
    }
    if (state === FIX_STATES.FAILED) {
        return (
            <span title={t('label.fix.failedHelper')}>
                <Chip label={t('label.fix.failed')} color="danger"/>
            </span>
        );
    }

    return null;
};

FixStatus.propTypes = {
    error: PropTypes.shape({
        fixed: PropTypes.bool
    }).isRequired,
    state: PropTypes.oneOf(Object.values(FIX_STATES))
};
