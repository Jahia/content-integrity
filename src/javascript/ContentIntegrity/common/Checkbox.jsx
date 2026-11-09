import React from 'react';
import PropTypes from 'prop-types';
import {Checkbox as MoonstoneCheckbox} from '@jahia/moonstone';

/**
 * Moonstone's Checkbox, controlled the same way on every Jahia version. Moonstone 1.6 (Jahia 8.1.5) builds it on
 * react-aria: it reads isSelected, and hands onChange to the input, which calls it with the change event alone.
 * Moonstone 2 reads checked and calls onChange(event, value, checked). This wrapper passes both props, and calls
 * onChange with the new checked state, read from the event.
 */
export const Checkbox = ({checked, onChange, ...props}) => (
    <MoonstoneCheckbox {...props}
                       checked={checked}
                       isSelected={checked}
                       onChange={eventOrSelected => onChange(typeof eventOrSelected === 'boolean' ? eventOrSelected : eventOrSelected.target.checked)}/>
);

Checkbox.propTypes = {
    checked: PropTypes.bool.isRequired,
    onChange: PropTypes.func.isRequired
};
