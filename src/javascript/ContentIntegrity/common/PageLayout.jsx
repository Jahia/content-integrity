import React from 'react';
import PropTypes from 'prop-types';
import styles from '../ContentIntegrity.scss';

/**
 * The page header above a scrollable content, as Moonstone's LayoutContent, which Moonstone 1.6 (Jahia 8.1.5) does
 * not have.
 */
export const PageLayout = ({header, content}) => (
    <div className={styles.pageLayout}>
        {header}
        <div className={styles.pageLayoutContent}>{content}</div>
    </div>
);

PageLayout.propTypes = {
    header: PropTypes.node,
    content: PropTypes.node
};
