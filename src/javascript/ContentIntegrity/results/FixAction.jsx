import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Build, Button} from '@jahia/moonstone';
import {FIX_STATES} from './useFixError';
import styles from '../ContentIntegrity.scss';

/**
 * The fix of an error, the main action of its details: the fix of the check which has detected it, with the values typed
 * in the details when the error is fixed with values. It is offered again after a failed fix, since the content may have
 * changed in between.
 */
export const FixAction = ({error, state, isDisabled, onClick}) => {
    const {t} = useTranslation('content-integrity');

    if (!error.fixable || error.fixed || state === FIX_STATES.FIXED) {
        return null;
    }

    return (
        <Button className={styles.fixButton}
                label={t('label.fix.fix')}
                icon={<Build/>}
                size="big"
                color="accent"
                isLoading={state === FIX_STATES.FIXING}
                isDisabled={isDisabled || state === FIX_STATES.FIXING}
                title={error.fixWithValues ? t('label.fix.withValuesHelper') : t('label.fix.helper', {check: error.checkName})}
                onClick={onClick}/>
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
    isDisabled: PropTypes.bool,
    onClick: PropTypes.func.isRequired
};
