import React, {useCallback} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {GET_ADMIN_PANEL_NODE} from '../ContentIntegrity.gql';
import styles from '../ContentIntegrity.scss';

const contextPath = () => window.contextJsParameters?.contextPath || '';

/*
 * The /tools JCR browser requires a tool access token. The module issues one through the
 * `toolsToken.json` view of its admin panel node, which requires `adminContentIntegrity`.
 * A token is requested at click time, so it is never stale.
 */
const useOpenInJcrBrowser = () => {
    const client = useApolloClient();
    return useCallback(async (uuid, workspace) => {
        // Open the window synchronously, so the popup blocker treats it as user initiated.
        const target = window.open('about:blank', '_blank');
        try {
            const {data} = await client.query({query: GET_ADMIN_PANEL_NODE, fetchPolicy: 'cache-first'});
            const path = data?.jcr?.nodesByQuery?.nodes?.[0]?.path;
            if (!path) {
                throw new Error('Content integrity admin panel node not found');
            }

            const response = await fetch(`${contextPath()}/cms/render/default/en${path}.toolsToken.json`, {credentials: 'same-origin'});
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const {token} = await response.json();
            const params = new URLSearchParams({workspace: workspace || 'default', uuid, toolAccessToken: token});
            if (target) {
                target.opener = null;
                target.location.href = `${contextPath()}/modules/tools/jcrBrowser.jsp?${params.toString()}`;
            }
        } catch (e) {
            console.error('Unable to open the JCR browser', e);
            target?.close();
        }
    }, [client]);
};

/**
 * A value which opens its node in the JCR browser. Its tooltip tells the full value, given as title, such as a path cut by
 * the width of its cell, then the action.
 */
export const JcrBrowserLink = ({uuid, workspace, title, children}) => {
    const {t} = useTranslation('content-integrity');
    const open = useOpenInJcrBrowser();
    if (!uuid) {
        return <>{children}</>;
    }

    return (
        <button type="button"
                className={styles.linkButton}
                title={title ? `${title}\n${t('label.openInJcrBrowser')}` : t('label.openInJcrBrowser')}
                onClick={event => {
                    event.stopPropagation();
                    open(uuid, workspace);
                }}
        >
            {children}
        </button>
    );
};

JcrBrowserLink.propTypes = {
    uuid: PropTypes.string,
    workspace: PropTypes.string,
    title: PropTypes.string,
    children: PropTypes.node
};
