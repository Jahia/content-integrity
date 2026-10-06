import React, {useLayoutEffect, useRef} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import styles from '../ContentIntegrity.scss';

// Follows the end of the log while the reader is already there, and leaves the scroll alone otherwise.
export const ScanLogs = ({logs}) => {
    const {t} = useTranslation('content-integrity');
    const ref = useRef(null);
    const stickToEnd = useRef(true);

    useLayoutEffect(() => {
        const el = ref.current;
        if (el && stickToEnd.current) {
            el.scrollTop = el.scrollHeight;
        }
    }, [logs]);

    return (
        <pre ref={ref}
             className={styles.logs}
             tabIndex={0}
             role="log"
             aria-live="polite"
             aria-label={t('label.scan.logs')}
             onScroll={e => {
                 const el = e.currentTarget;
                 stickToEnd.current = el.scrollTop + el.clientHeight >= el.scrollHeight - 4;
             }}
        >
            {logs.join('\n')}
        </pre>
    );
};

ScanLogs.propTypes = {
    logs: PropTypes.arrayOf(PropTypes.string).isRequired
};
