import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, HelpOutline, Tune, Typography} from '@jahia/moonstone';
import {Checkbox} from '../common/Checkbox';
import {CheckConfigDialog} from './CheckConfigDialog';
import styles from '../ContentIntegrity.scss';

export const ChecksSelector = ({checks, selected, onChange, isDisabled}) => {
    const {t} = useTranslation('content-integrity');
    const [configuring, setConfiguring] = useState(null);

    const toggle = (id, checked) => onChange(checked ? [...selected, id] : selected.filter(s => s !== id));

    return (
        <section className={styles.section} aria-labelledby="ci-checks-title">
            <div className={styles.sectionHeader}>
                <Typography id="ci-checks-title" variant="subheading" weight="bold">
                    {t('label.checks.title', {selected: selected.length, total: checks.length})}
                </Typography>
                <div className={styles.sectionActions}>
                    <Button label={t('label.checks.selectAll')}
                            variant="ghost"
                            size="small"
                            isDisabled={isDisabled}
                            onClick={() => onChange(checks.map(c => c.id))}/>
                    <Button label={t('label.checks.unselectAll')}
                            variant="ghost"
                            size="small"
                            isDisabled={isDisabled}
                            onClick={() => onChange([])}/>
                </div>
            </div>
            <ul className={styles.checksGrid}>
                {checks.map(check => (
                    <li key={check.id} className={styles.checkItem}>
                        <label className={styles.checkboxLabel} htmlFor={`ci-check-${check.id}`}>
                            <Checkbox id={`ci-check-${check.id}`}
                                      value={check.id}
                                      checked={selected.includes(check.id)}
                                      isDisabled={isDisabled}
                                      onChange={checked => toggle(check.id, checked)}/>
                            <Typography variant="body" component="span" className={styles.checkName}>{check.id}</Typography>
                        </label>
                        <span className={styles.checkActions}>
                            {check.documentation && (
                                <a className={styles.iconLink}
                                   href={check.documentation}
                                   target="_blank"
                                   rel="noopener noreferrer"
                                   title={t('label.checks.documentation', {check: check.id})}
                                   aria-label={t('label.checks.documentation', {check: check.id})}
                                >
                                    <HelpOutline/>
                                </a>
                            )}
                            {/* Without a configuration, its place is kept so that the icons line up in columns */}
                            {!check.configurable && <span className={styles.iconSlot}/>}
                            {check.configurable && (
                                <Button variant="ghost"
                                        icon={<Tune/>}
                                        title={t('label.checks.configure', {check: check.id})}
                                        aria-label={t('label.checks.configure', {check: check.id})}
                                        onClick={() => setConfiguring(check.id)}/>
                            )}
                        </span>
                    </li>
                ))}
            </ul>
            {configuring && <CheckConfigDialog checkId={configuring} onClose={() => setConfiguring(null)}/>}
        </section>
    );
};

ChecksSelector.propTypes = {
    checks: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        configurable: PropTypes.bool,
        documentation: PropTypes.string
    })).isRequired,
    selected: PropTypes.arrayOf(PropTypes.string).isRequired,
    onChange: PropTypes.func.isRequired,
    isDisabled: PropTypes.bool
};
