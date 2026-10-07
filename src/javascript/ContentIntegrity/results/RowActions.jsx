import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Build, Button, Chip, Information, Loader, Menu, MenuItem, MoreVert} from '@jahia/moonstone';
import {FIX_STATES} from './useFixError';
import styles from '../ContentIntegrity.scss';

/**
 * The actions on an error of the results table, in a menu opened from the 3 dots of its row: its details, and its fix when
 * the check which has detected it provides one and the user may fix the errors. The outcome of a fix stays displayed in the row.
 */
export const RowActions = ({error, state, canFixErrors, onFix, onOpenDetails}) => {
    const {t} = useTranslation('content-integrity');
    const anchorEl = useRef(null);
    const [isOpen, setOpen] = useState(false);

    // The Moonstone menu closes on a click outside of it: Escape closes it too, and gives the focus back to its button
    useEffect(() => {
        if (!isOpen) {
            return undefined;
        }

        const onKeyDown = event => {
            if (event.key === 'Escape') {
                setOpen(false);
                anchorEl.current?.querySelector('button')?.focus();
            }
        };

        document.addEventListener('keydown', onKeyDown);
        return () => document.removeEventListener('keydown', onKeyDown);
    }, [isOpen]);

    const isFixed = error.fixed || state === FIX_STATES.FIXED;
    const isFixing = state === FIX_STATES.FIXING;
    const canFix = canFixErrors && error.fixable && !isFixed && !isFixing && state !== FIX_STATES.FAILED;

    const run = action => event => {
        event.stopPropagation();
        setOpen(false);
        action();
    };

    return (
        <div className={styles.rowActions}>
            {isFixing && <Loader size="small"/>}
            {isFixed && <Chip label={t('label.fix.fixed')} color="success"/>}
            {state === FIX_STATES.FAILED && (
                <span title={t('label.fix.failedHelper')}>
                    <Chip label={t('label.fix.failed')} color="danger"/>
                </span>
            )}
            <div ref={anchorEl}>
                <Button variant="ghost"
                        icon={<MoreVert/>}
                        title={t('label.results.actions')}
                        aria-label={t('label.results.actions')}
                        aria-haspopup="menu"
                        aria-expanded={isOpen}
                        onClick={event => {
                            event.stopPropagation();
                            setOpen(true);
                        }}/>
            </div>
            {/* Mounted only while open: a closed Moonstone menu stays in the DOM, once per row */}
            {isOpen && (
                <Menu isDisplayed
                      anchorEl={anchorEl}
                      anchorElOrigin={{horizontal: 'right', vertical: 'bottom'}}
                      transformElOrigin={{horizontal: 'right', vertical: 'top'}}
                      hasSearch={false}
                      minWidth="180px"
                      onClose={() => setOpen(false)}
                >
                    <MenuItem label={t('label.results.details')}
                              iconStart={<Information/>}
                              iconSize="default"
                              onClick={run(() => onOpenDetails(error.id))}/>
                    {canFix && (
                        <MenuItem label={error.fixWithValues ? t('label.fix.withValues') : t('label.fix.fix')}
                                  iconStart={<Build/>}
                                  iconSize="default"
                                  title={error.fixWithValues ? t('label.fix.withValuesHelper') : t('label.fix.helper', {check: error.checkName})}
                                  onClick={run(() => (error.fixWithValues ? onOpenDetails(error.id) : onFix(error.id)))}/>
                    )}
                </Menu>
            )}
        </div>
    );
};

RowActions.propTypes = {
    error: PropTypes.shape({
        id: PropTypes.string.isRequired,
        checkName: PropTypes.string,
        fixed: PropTypes.bool,
        fixable: PropTypes.bool,
        fixWithValues: PropTypes.bool
    }).isRequired,
    state: PropTypes.oneOf(Object.values(FIX_STATES)),
    canFixErrors: PropTypes.bool,
    onFix: PropTypes.func.isRequired,
    onOpenDetails: PropTypes.func.isRequired
};
