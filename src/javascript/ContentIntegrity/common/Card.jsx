import React from 'react';
import PropTypes from 'prop-types';
import styles from '../ContentIntegrity.scss';

/**
 * A white surface with a shadow, as Moonstone's Paper, which Moonstone 1.6 (Jahia 8.1.5) does not have.
 */
export const Card = ({className, children}) => (
    <section className={[styles.paper, className || ''].filter(Boolean).join(' ')}>
        {children}
    </section>
);

Card.propTypes = {
    className: PropTypes.string,
    children: PropTypes.node
};
