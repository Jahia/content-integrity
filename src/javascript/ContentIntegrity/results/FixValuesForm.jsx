import React from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Add, Button, Close, Dropdown, Input, Typography} from '@jahia/moonstone';
import {FormField} from '../common/FormField';
import styles from '../ContentIntegrity.scss';

const BOOLEAN_CHOICES = ['true', 'false'];

const PLACEHOLDERS = {
    Date: '2026-01-31T12:00',
    Reference: '/sites/mySite/contents/myNode',
    WeakReference: '/sites/mySite/contents/myNode'
};

const getChoices = definition => {
    if (definition.choices?.length) {
        return definition.choices;
    }

    return definition.type === 'Boolean' ? BOOLEAN_CHOICES : null;
};

/**
 * The values typed by the administrator, before the default values of the definition when it has some.
 */
export const initialFixValues = definition => (definition?.defaultValues?.length ? definition.defaultValues : ['']);

export const typedFixValues = values => values.filter(value => value.trim().length > 0);

/**
 * The values an administrator types to fix an error, such as the value of a missing mandatory property. The Fix button,
 * passed as children, is displayed below the fields and sends them: Enter in a field does too. The server converts them to the type of the definition and
 * checks its constraints: its message is shown on the field.
 */
export const FixValuesForm = ({errorId, definition, values, message, isDisabled, onChange, onSubmit, children}) => {
    const {t} = useTranslation('content-integrity');
    const choices = getChoices(definition);
    const fieldId = `ci-fix-values-${errorId}`;

    const setValue = (index, value) => onChange(values.map((v, i) => (i === index ? value : v)));

    const helper = [
        t('label.fixValues.type', {type: definition.type}),
        definition.constraints?.length && !definition.choices?.length ? t('label.fixValues.constraints', {constraints: definition.constraints.join(', ')}) : null
    ].filter(Boolean).join(' · ');

    const renderValue = (value, index) => {
        const label = definition.multiple ? t('label.fixValues.valueAt', {index: index + 1}) : definition.name;
        const editor = choices ? (
            <Dropdown data={choices.map(choice => ({label: choice, value: choice}))}
                      value={value || undefined}
                      placeholder={t('label.fixValues.choose')}
                      variant="outlined"
                      isDisabled={isDisabled}
                      className={styles.fixValuesInput}
                      onChange={(e, item) => setValue(index, item.value)}/>
        ) : (
            <Input id={`${fieldId}-${index}`}
                   aria-label={label}
                   value={value}
                   placeholder={PLACEHOLDERS[definition.type] || ''}
                   isDisabled={isDisabled}
                   className={styles.fixValuesInput}
                   onChange={e => setValue(index, e.target.value)}
                   onKeyPress={e => {
                       if (e.key === 'Enter') {
                           e.preventDefault();
                           onSubmit();
                       }
                   }}/>
        );

        return (
            <div key={index} className={styles.fixValuesRow}>
                {editor}
                {definition.multiple && values.length > 1 && (
                    <Button icon={<Close/>}
                            variant="ghost"
                            title={t('label.fixValues.remove')}
                            aria-label={t('label.fixValues.remove')}
                            isDisabled={isDisabled}
                            onClick={() => onChange(values.filter((v, i) => i !== index))}/>
                )}
            </div>
        );
    };

    return (
        <section className={styles.fixValuesSection}>
            <div>
                <Typography variant="subheading" weight="bold">{t('label.fixValues.title')}</Typography>
                <Typography variant="body" className={styles.helper}>{t('label.fixValues.description', {property: definition.name})}</Typography>
            </div>
            <FormField id={`${fieldId}-field`}
                       className={styles.fixValuesField}
                       label={definition.name}
                       helper={helper}
                       hasError={Boolean(message)}
                       errorMessage={message || undefined}
            >
                <div className={styles.fixValues}>
                    {values.map(renderValue)}
                    {definition.multiple && (
                        <div>
                            <Button label={t('label.fixValues.add')}
                                    icon={<Add/>}
                                    variant="ghost"
                                    isDisabled={isDisabled}
                                    onClick={() => onChange([...values, ''])}/>
                        </div>
                    )}
                </div>
            </FormField>
            {children && <div className={styles.fixActionRow}>{children}</div>}
        </section>
    );
};

FixValuesForm.propTypes = {
    errorId: PropTypes.string.isRequired,
    definition: PropTypes.shape({
        name: PropTypes.string.isRequired,
        type: PropTypes.string.isRequired,
        multiple: PropTypes.bool,
        choices: PropTypes.arrayOf(PropTypes.string),
        constraints: PropTypes.arrayOf(PropTypes.string),
        defaultValues: PropTypes.arrayOf(PropTypes.string)
    }).isRequired,
    values: PropTypes.arrayOf(PropTypes.string).isRequired,
    message: PropTypes.string,
    isDisabled: PropTypes.bool,
    onChange: PropTypes.func.isRequired,
    onSubmit: PropTypes.func.isRequired,
    children: PropTypes.node
};
