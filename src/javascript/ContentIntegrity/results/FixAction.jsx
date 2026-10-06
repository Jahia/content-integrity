import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Build, Button, Chip} from '@jahia/moonstone';
import {FIX_STATES} from './useFixError';

/**
 * The fix of an error: a button when the check which has detected it provides a fix, then the outcome.
 * When the fix takes values, the button opens the details of the error, where the values are typed.
 */
export const FixAction = ({error, state, onFix, onOpenValues, size}) => {
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

    if (error.fixWithValues && onOpenValues) {
        return (
            <Button label={t('label.fix.withValues')}
                    icon={<Build/>}
                    variant="outlined"
                    size={size}
                    title={t('label.fix.withValuesHelper')}
                    onClick={event => {
                        event.stopPropagation();
                        onOpenValues(error.id);
                    }}/>
        );
    }

    return (
        <Button label={t('label.fix.fix')}
                icon={<Build/>}
                variant="outlined"
                size={size}
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
        fixable: PropTypes.bool,
        fixWithValues: PropTypes.bool
    }).isRequired,
    state: PropTypes.oneOf(Object.values(FIX_STATES)),
    onFix: PropTypes.func.isRequired,
    onOpenValues: PropTypes.func,
    size: PropTypes.oneOf(['small', 'default', 'big'])
};

FixAction.defaultProps = {
    size: 'small'
};
