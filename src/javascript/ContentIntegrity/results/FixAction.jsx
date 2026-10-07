import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Build, Button, Chip} from '@jahia/moonstone';
import {FIX_STATES} from './useFixError';

/**
 * The fix of an error in its details dialog: a button when the check which has detected it provides a fix, then the outcome.
 */
export const FixAction = ({error, state, onFix}) => {
    const {t} = useTranslation('content-integrity');

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
    if (!error.fixable) return null;

    return (
        <Button label={t('label.fix.fix')}
                icon={<Build/>}
                variant="outlined"
                isLoading={state === FIX_STATES.FIXING}
                isDisabled={state === FIX_STATES.FIXING}
                title={t('label.fix.helper', {check: error.checkName})}
                onClick={event => {
                    event.stopPropagation();
                    onFix(error.id);
                }}/>
    );
};

FixAction.propTypes = {
    error: PropTypes.shape({
        id: PropTypes.string.isRequired,
        checkName: PropTypes.string,
        fixed: PropTypes.bool,
        fixable: PropTypes.bool
    }).isRequired,
    state: PropTypes.oneOf(Object.values(FIX_STATES)),
    onFix: PropTypes.func.isRequired
};
