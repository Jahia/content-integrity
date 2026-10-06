import React, {useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Checkbox, Field, Input, Loader, Typography} from '@jahia/moonstone';
import {Dialog} from '../common/Dialog';
import {buildSaveConfigurationsQuery, GET_CHECK_CONFIGURATIONS, RESET_CHECK_CONFIGURATIONS} from '../ContentIntegrity.gql';
import styles from '../ContentIntegrity.scss';

const SUPPORTED_TYPES = ['string', 'integer', 'boolean'];

const isValid = (type, value) => type !== 'integer' || /^-?\d+$/.test(String(value).trim());

export const CheckConfigDialog = ({checkId, onClose}) => {
    const {t} = useTranslation('content-integrity');
    const client = useApolloClient();
    const {data, loading, error} = useQuery(GET_CHECK_CONFIGURATIONS, {
        variables: {id: checkId},
        fetchPolicy: 'no-cache'
    });
    const [values, setValues] = useState({});
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState(null);

    const configurations = useMemo(() => [...(data?.integrity?.check?.configurations || [])]
        .filter(c => SUPPORTED_TYPES.includes(c.type))
        .sort((a, b) => a.rank - b.rank), [data]);

    useEffect(() => {
        setValues(Object.fromEntries(configurations.map(c => [c.name, c.value ?? ''])));
    }, [configurations]);

    const changed = configurations.filter(c => String(values[c.name] ?? '') !== String(c.value ?? ''));
    const invalid = configurations.filter(c => !isValid(c.type, values[c.name] ?? ''));

    const run = (query, variables) => {
        setSaving(true);
        setSaveError(null);
        client.query({query, variables, fetchPolicy: 'no-cache'})
            .then(({errors}) => {
                if (errors?.length) {
                    throw new Error(errors.map(e => e.message).join(', '));
                }

                onClose();
            })
            .catch(e => setSaveError(e.message))
            .finally(() => setSaving(false));
    };

    const save = () => {
        const variables = {id: checkId};
        changed.forEach((c, i) => {
            variables[`n${i}`] = c.name;
            variables[`v${i}`] = String(values[c.name]).trim();
        });
        run(buildSaveConfigurationsQuery(changed.length), variables);
    };

    const renderInput = conf => {
        const id = `ci-conf-${conf.name}`;
        const value = values[conf.name] ?? '';
        if (conf.type === 'boolean') {
            return (
                <div key={conf.name} className={styles.configItem}>
                    <label className={styles.checkboxLabel} htmlFor={id}>
                        <Checkbox id={id}
                                  checked={value === 'true'}
                                  onChange={(e, v, checked) => setValues(prev => ({...prev, [conf.name]: checked ? 'true' : 'false'}))}/>
                        <Typography variant="body" component="span" weight="semiBold">{conf.name}</Typography>
                    </label>
                    {conf.description && <Typography variant="caption" className={styles.helper}>{conf.description}</Typography>}
                </div>
            );
        }

        const hasError = !isValid(conf.type, value);
        return (
            <Field key={conf.name}
                   id={`${id}-field`}
                   label={conf.name}
                   helper={conf.description || undefined}
                   hasError={hasError}
                   errorMessage={hasError ? t('label.config.integerExpected') : undefined}
                   className={styles.configItem}
            >
                <Input id={id}
                       aria-label={conf.name}
                       value={value}
                       placeholder={conf.defaultValue ?? ''}
                       onChange={e => {
                           const next = e.target.value;
                           setValues(prev => ({...prev, [conf.name]: next}));
                       }}/>
            </Field>
        );
    };

    return (
        <Dialog isOpen
                title={t('label.config.title', {check: checkId})}
                size="large"
                actions={(
                    <>
                        <Button label={t('label.config.reset')}
                                variant="ghost"
                                isDisabled={saving || loading}
                                onClick={() => run(RESET_CHECK_CONFIGURATIONS, {id: checkId})}/>
                        <div className={styles.spacer}/>
                        <Button label={t('label.cancel')} variant="outlined" onClick={onClose}/>
                        <Button label={t('label.save')}
                                color="accent"
                                isDisabled={saving || loading || changed.length === 0 || invalid.length > 0}
                                onClick={save}/>
                    </>
                )}
                onClose={onClose}
        >
            {loading && <div className={styles.centered}><Loader size="big"/></div>}
            {error && <Typography className={styles.error}>{error.message}</Typography>}
            {!loading && !error && configurations.length === 0 && (
                <Typography variant="body">{t('label.config.none')}</Typography>
            )}
            {configurations.map(renderInput)}
            {saveError && <Typography className={styles.error}>{saveError}</Typography>}
        </Dialog>
    );
};

CheckConfigDialog.propTypes = {
    checkId: PropTypes.string.isRequired,
    onClose: PropTypes.func.isRequired
};
