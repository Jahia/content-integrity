import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Add, Button, Chip, Close, Field, Input} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

export const ExcludedPaths = ({paths, onChange, isDisabled}) => {
    const {t} = useTranslation('content-integrity');
    const [value, setValue] = useState('');

    const add = () => {
        const path = value.trim();
        if (path.length > 0 && !paths.includes(path)) {
            onChange([...paths, path]);
        }

        setValue('');
    };

    return (
        <Field id="ci-excluded-paths-field"
               label={t('label.scan.excludedPaths')}
               helper={t('label.scan.excludedPathsHelper')}
        >
            <div className={styles.inputWithButton}>
                <div className={styles.inputWithButtonInput}>
                    <Input id="ci-excluded-paths"
                           aria-label={t('label.scan.excludedPaths')}
                           value={value}
                           placeholder="/sites/mySite/files"
                           isDisabled={isDisabled}
                           onChange={e => setValue(e.target.value)}
                           onKeyPress={e => {
                               if (e.key === 'Enter') {
                                   e.preventDefault();
                                   add();
                               }
                           }}/>
                </div>
                <Button label={t('label.add')} icon={<Add/>} variant="outlined" isDisabled={isDisabled || value.trim().length === 0} onClick={add}/>
            </div>
            {paths.length > 0 && (
                <ul className={styles.chips} aria-label={t('label.scan.excludedPaths')}>
                    {paths.map(path => (
                        <li key={path}>
                            <button type="button"
                                    className={styles.chipButton}
                                    disabled={isDisabled}
                                    title={t('label.scan.removeExcludedPath', {path})}
                                    aria-label={t('label.scan.removeExcludedPath', {path})}
                                    onClick={() => onChange(paths.filter(p => p !== path))}
                            >
                                <Chip label={path} icon={<Close/>} color="accent"/>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </Field>
    );
};

ExcludedPaths.propTypes = {
    paths: PropTypes.arrayOf(PropTypes.string).isRequired,
    onChange: PropTypes.func.isRequired,
    isDisabled: PropTypes.bool
};
