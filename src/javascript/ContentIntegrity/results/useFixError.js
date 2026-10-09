import {useCallback, useEffect, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {FIX_ERROR} from '../ContentIntegrity.gql';

export const FIX_STATES = {FIXING: 'fixing', FIXED: 'fixed', FAILED: 'failed'};

const errorMessage = e => (e?.graphQLErrors?.length ? e.graphQLErrors.map(err => err.message).join(', ') : e?.message);

/**
 * Fixes the errors of a scan, one at a time, with the fix of the check which has detected each of them.
 * Keeps the state of each fix, keyed by error ID, until another scan is displayed.
 *
 * When values are passed, they are typed by the administrator: a rejected value is not a failed fix, so the previous
 * state comes back, such as the failure of the fix without values, to let the administrator correct the value, and the
 * reason is returned.
 */
export const useFixError = (resultsId, onFixed) => {
    const client = useApolloClient();
    const [states, setStates] = useState({});

    useEffect(() => {
        setStates({});
    }, [resultsId]);

    const setState = (id, state) => setStates(prev => {
        const next = {...prev};
        if (state) {
            next[id] = state;
        } else {
            delete next[id];
        }

        return next;
    });

    const fix = useCallback((errorId, values) => {
        const withValues = values !== undefined;
        const previous = states[errorId] === FIX_STATES.FIXING ? undefined : states[errorId];
        setState(errorId, FIX_STATES.FIXING);
        return client.query({query: FIX_ERROR, variables: {resultsID: resultsId, id: errorId, values}, fetchPolicy: 'no-cache'})
            .then(({data, errors}) => {
                if (errors?.length) {
                    throw new Error(errors.map(e => e.message).join(', '));
                }

                const fixed = data?.integrity?.results?.error?.fixed === true;
                setState(errorId, fixed ? FIX_STATES.FIXED : (withValues ? previous : FIX_STATES.FAILED));
                if (fixed && onFixed) {
                    onFixed(errorId);
                }

                return {fixed};
            })
            .catch(e => {
                console.error('Impossible to fix the error', errorId, e);
                setState(errorId, withValues ? previous : FIX_STATES.FAILED);
                return {fixed: false, message: errorMessage(e)};
            });
    }, [client, resultsId, onFixed, states]);

    return {fix, states};
};
