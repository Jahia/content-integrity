import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Input, RadioGroup, RadioItem, Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

// Above this number of values, a search narrows the list
const SEARCH_THRESHOLD = 10;

/**
 * The values of a filter in its selector, with the count of the errors of each value. A value applies when it is chosen.
 */
export const FilterOptions = ({column, label, options, value, onChange}) => {
    const {t} = useTranslation('content-integrity');
    const [search, setSearch] = useState('');
    const needle = search.trim().toLowerCase();
    const shown = needle === '' ? options : options.filter(o => o.label.toLowerCase().includes(needle));
    return (
        <div className={styles.filterOptions}>
            {options.length > SEARCH_THRESHOLD && (
                <Input variant="search"
                       value={search}
                       aria-label={t('label.results.searchValues')}
                       placeholder={t('label.results.searchValues')}
                       onChange={event => setSearch(event.target.value)}
                       onClear={() => setSearch('')}/>
            )}
            {shown.length > 0 ? (
                <RadioGroup name={`ci-filter-${column}`} aria-label={label} value={value} onChange={(event, selected) => onChange(selected)}>
                    {shown.map(o => <RadioItem key={o.value} value={o.value} label={o.label}/>)}
                </RadioGroup>
            ) : (
                <Typography variant="caption">{t('label.results.noValueMatch')}</Typography>
            )}
        </div>
    );
};

FilterOptions.propTypes = {
    column: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    options: PropTypes.arrayOf(PropTypes.shape({value: PropTypes.string.isRequired, label: PropTypes.string.isRequired})).isRequired,
    // The value of the chosen option, or '' when the filter has none
    value: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};
