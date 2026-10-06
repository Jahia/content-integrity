import React, {useEffect, useId, useRef} from 'react';
import PropTypes from 'prop-types';
import {Button, Close, Paper, Typography} from '@jahia/moonstone';
import styles from '../ContentIntegrity.scss';

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modal dialog built on Moonstone primitives. Moonstone 2.14, the version the Jahia runtime shares,
 * ships no Modal component. Escape closes it, focus is trapped inside it while open and restored
 * to the trigger on close.
 */
export const Dialog = ({isOpen, title, onClose, actions, children, size}) => {
    const dialogRef = useRef(null);
    const titleId = useId();
    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;

    useEffect(() => {
        if (!isOpen) {
            return undefined;
        }

        const previouslyFocused = document.activeElement;
        const first = dialogRef.current?.querySelector(FOCUSABLE);
        first?.focus();

        const onKeyDown = event => {
            // With stacked dialogs (a check configuration opened from the new scan dialog), only the
            // last one opened handles the keyboard.
            const dialogs = document.querySelectorAll('[role=dialog]');
            if (dialogs[dialogs.length - 1] !== dialogRef.current) {
                return;
            }

            if (event.key === 'Escape') {
                event.stopPropagation();
                onCloseRef.current();
                return;
            }

            if (event.key === 'Tab' && dialogRef.current) {
                const focusables = [...dialogRef.current.querySelectorAll(FOCUSABLE)];
                if (focusables.length === 0) {
                    return;
                }

                const firstEl = focusables[0];
                const lastEl = focusables[focusables.length - 1];
                if (event.shiftKey && document.activeElement === firstEl) {
                    event.preventDefault();
                    lastEl.focus();
                } else if (!event.shiftKey && document.activeElement === lastEl) {
                    event.preventDefault();
                    firstEl.focus();
                }
            }
        };

        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('keydown', onKeyDown);
            previouslyFocused?.focus?.();
        };
    }, [isOpen]);

    if (!isOpen) {
        return null;
    }

    return (
        <div className={styles.dialogBackdrop}
             onMouseDown={event => {
                 if (event.target === event.currentTarget) {
                     onClose();
                 }
             }}
        >
            <div ref={dialogRef}
                 className={[styles.dialog, size === 'large' && styles.dialogLarge, size === 'xlarge' && styles.dialogXLarge].filter(Boolean).join(' ')}
                 role="dialog"
                 aria-modal="true"
                 aria-labelledby={titleId}
            >
                <Paper className={styles.dialogPaper}>
                    <div className={styles.dialogHeader}>
                        <Typography id={titleId} variant="heading" weight="bold">{title}</Typography>
                        <Button variant="ghost" icon={<Close/>} aria-label="Close" onClick={onClose}/>
                    </div>
                    <div className={styles.dialogBody}>{children}</div>
                    {actions && <div className={styles.dialogActions}>{actions}</div>}
                </Paper>
            </div>
        </div>
    );
};

Dialog.propTypes = {
    isOpen: PropTypes.bool.isRequired,
    title: PropTypes.string.isRequired,
    onClose: PropTypes.func.isRequired,
    actions: PropTypes.node,
    children: PropTypes.node,
    size: PropTypes.oneOf(['default', 'large', 'xlarge'])
};
