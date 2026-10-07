import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Download, Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

const contextPath = () => window.contextJsParameters?.contextPath || '';

// Only the reports uploaded to the JCR can be downloaded from the browser.
export const ReportLinks = ({reports}) => {
    const {t} = useTranslation('content-integrity');
    const files = (reports || []).filter(r => r.location === 'JCR' && r.uri);
    if (files.length === 0) {
        return null;
    }

    return (
        <div className={styles.reports}>
            <Typography variant="body" weight="semiBold">
                {t('label.reports', {count: files.length})}
            </Typography>
            <ul className={styles.reportList}>
                {files.map(file => (
                    <li key={file.uri}>
                        <a className={styles.reportLink}
                           href={`${contextPath()}/files/default${encodeURI(file.uri)}`}
                           target="_blank"
                           rel="noopener noreferrer"
                        >
                            <Download/>
                            <Typography variant="body" component="span">{file.name}</Typography>
                        </a>
                    </li>
                ))}
            </ul>
        </div>
    );
};

ReportLinks.propTypes = {
    reports: PropTypes.arrayOf(PropTypes.shape({
        name: PropTypes.string,
        location: PropTypes.string,
        uri: PropTypes.string
    }))
};
