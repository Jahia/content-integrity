import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {Button, Typography} from '@jahia/moonstone';
import {Checkbox} from './Checkbox';
import {Dialog} from './Dialog';
import styles from '../ContentIntegrity.scss';

/**
 * The confirmation of an action which alters the data of the server, such as a fix or a deletion. With requiresAcknowledgement,
 * the action is confirmed once the administrator has ticked that the data are going to be altered: the box is unticked
 * each time the confirmation opens. The children, such as a description and a warning, are spaced to be read one after
 * the other.
 */
export const ConfirmDialog = ({isOpen, title, confirmLabel, icon, isPending = false, requiresAcknowledgement = false, onConfirm, onClose, children}) => {
    const {t} = useTranslation('content-integrity');
    const [isAcknowledged, setAcknowledged] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setAcknowledged(false);
        }
    }, [isOpen]);

    return (
        <Dialog isOpen={isOpen}
                title={title}
                actions={(
                    <>
                        <Button label={t('label.cancel')} variant="outlined" isDisabled={isPending} onClick={onClose}/>
                        <Button label={confirmLabel}
                                icon={icon}
                                color="danger"
                                isLoading={isPending}
                                isDisabled={isPending || (requiresAcknowledgement && !isAcknowledged)}
                                onClick={onConfirm}/>
                    </>
                )}
                onClose={() => !isPending && onClose()}
        >
            <div className={styles.confirmBody}>
                {children}
                {requiresAcknowledgement && (
                    <label className={styles.checkboxLabel} htmlFor="ci-acknowledge">
                        <Checkbox id="ci-acknowledge"
                                  checked={isAcknowledged}
                                  isDisabled={isPending}
                                  onChange={checked => setAcknowledged(checked)}/>
                        <Typography variant="body" component="span" weight="semiBold">{t('label.acknowledge')}</Typography>
                    </label>
                )}
            </div>
        </Dialog>
    );
};

ConfirmDialog.propTypes = {
    isOpen: PropTypes.bool.isRequired,
    title: PropTypes.string.isRequired,
    confirmLabel: PropTypes.string.isRequired,
    icon: PropTypes.node,
    isPending: PropTypes.bool,
    requiresAcknowledgement: PropTypes.bool,
    onConfirm: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    children: PropTypes.node
};
