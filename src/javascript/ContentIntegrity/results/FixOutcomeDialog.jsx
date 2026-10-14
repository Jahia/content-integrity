import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Typography} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import styles from '../ContentIntegrity.scss';

const sum = items => items.reduce((total, item) => total + item.count, 0);

/**
 * The outcome of a fix of several errors: how many are fixed, how many are skipped and why, how many are not fixed and why.
 * A fix which has failed without exception tells no reason: its description, which says when it can't fix, is displayed
 * instead, and a single note tells for all of them where the details are.
 *
 * @param outcome {fixed, skipped: [{reason, count}], failures: [{checkName, errorType, message, count}]}
 */
export const FixOutcomeDialog = ({outcome, onClose}) => {
    const {t, i18n} = useTranslation('content-integrity');
    if (!outcome) {
        return null;
    }

    const skippedCount = sum(outcome.skipped);
    const failedCount = sum(outcome.failures);
    const descriptionKey = failure => `label.fixDescription.${failure.checkName}.${failure.errorType}`;

    return (
        <Dialog isOpen
                title={t('label.fixOutcome.title')}
                actions={<Button label={t('label.close')} variant="outlined" onClick={onClose}/>}
                onClose={onClose}
        >
            <section data-fix-outcome="fixed" className={styles.fixOutcome}>
                <Typography weight="bold">{t('label.fixOutcome.fixed', {count: outcome.fixed})}</Typography>
            </section>
            <section data-fix-outcome="skipped" className={styles.fixOutcome}>
                <Typography weight="bold">{t('label.fixOutcome.skipped', {count: skippedCount})}</Typography>
                {skippedCount > 0 && (
                    <ul className={styles.fixOutcomeList}>
                        {outcome.skipped.filter(s => s.count > 0).map(s => (
                            <li key={s.reason} data-reason={s.reason}>
                                <Typography>{t(`label.fixOutcome.reason.${s.reason}`, {count: s.count})}</Typography>
                            </li>
                        ))}
                    </ul>
                )}
            </section>
            <section data-fix-outcome="failed" className={styles.fixOutcome}>
                <Typography weight="bold">{t('label.fixOutcome.failed', {count: failedCount})}</Typography>
                {outcome.failures.some(f => !f.message) && <Typography data-no-message>{t('label.fixOutcome.noMessage')}</Typography>}
                {failedCount > 0 && (
                    <ul className={styles.fixOutcomeList}>
                        {outcome.failures.map(f => (
                            <li key={[f.checkName, f.errorType, f.message].join('|')} data-error-type={f.errorType}>
                                <Typography weight="semiBold">{t('label.fixOutcome.failure', {check: f.checkName, type: f.errorType, count: f.count})}</Typography>
                                {f.message && <Typography>{f.message}</Typography>}
                                {!f.message && i18n.exists(descriptionKey(f), {ns: 'content-integrity'}) && (
                                    <Typography className={styles.helper}>{t('label.fixOutcome.fixDoes')} {t(descriptionKey(f))}</Typography>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </section>
        </Dialog>
    );
};

FixOutcomeDialog.propTypes = {
    outcome: PropTypes.shape({
        fixed: PropTypes.number.isRequired,
        skipped: PropTypes.arrayOf(PropTypes.shape({reason: PropTypes.string, count: PropTypes.number})).isRequired,
        failures: PropTypes.arrayOf(PropTypes.shape({
            checkName: PropTypes.string,
            errorType: PropTypes.string,
            message: PropTypes.string,
            count: PropTypes.number
        })).isRequired
    }),
    onClose: PropTypes.func.isRequired
};
