import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Download, Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

const contextPath = () => window.contextJsParameters?.contextPath || '';

const extension = name => {
    const dot = name.lastIndexOf('.');
    return dot < 0 ? name : name.substring(dot + 1).toUpperCase();
};

// Only the reports uploaded to the JCR can be downloaded from the browser.
// The links sit next to the scan selector, which already names the scan, so they show only the format.
export const ReportLinks = ({reports}) => {
    const {t} = useTranslation('content-integrity');
    const files = (reports || []).filter(r => r.location === 'JCR' && r.uri);
    if (files.length === 0) {
        return null;
    }

    return (
        <div className={styles.filter}>
            <Typography variant="caption" weight="semiBold">
                {t('label.reports', {count: files.length})}
            </Typography>
            <ul className={styles.reportList}>
                {files.map(file => (
                    <li key={file.uri}>
                        <a className={styles.reportLink}
                           href={`${contextPath()}/files/default${encodeURI(file.uri)}`}
                           target="_blank"
                           rel="noopener noreferrer"
                           title={file.name}
                        >
                            <Download/>
                            <Typography variant="body" component="span">{extension(file.name)}</Typography>
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
