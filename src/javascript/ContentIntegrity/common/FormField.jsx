import React from 'react';
import PropTypes from 'prop-types';
import {Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

/**
 * A labelled form field, laid out as Moonstone's Field: a label, a helper, the inputs, then the error message.
 * Moonstone 2.5, the version Jahia 8.1 shares, has no Field component, so the module draws its own.
 */
export const FormField = ({id, label, helper, hasError, errorMessage, className, children}) => (
    <div id={id} className={[styles.field, hasError ? styles.fieldError : '', className || ''].filter(Boolean).join(' ')}>
        <Typography component="label" weight="bold">{label}</Typography>
        {helper && <Typography variant="caption" className={styles.fieldHelper}>{helper}</Typography>}
        <div className={styles.fieldChildren}>
            {children}
            {hasError && errorMessage && <Typography variant="caption" className={styles.fieldErrorMessage}>{errorMessage}</Typography>}
        </div>
    </div>
);

FormField.propTypes = {
    id: PropTypes.string,
    label: PropTypes.node.isRequired,
    helper: PropTypes.node,
    hasError: PropTypes.bool,
    errorMessage: PropTypes.node,
    className: PropTypes.string,
    children: PropTypes.node
};
