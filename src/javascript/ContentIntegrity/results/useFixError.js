import {useCallback, useEffect, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {FIX_ERRORS} from '../ContentIntegrity.gql';

export const FIX_STATES = {FIXING: 'fixing', FIXED: 'fixed', FAILED: 'failed'};

const errorMessage = e => (e?.graphQLErrors?.length ? e.graphQLErrors.map(err => err.message).join(', ') : e?.message);

/**
 * Fixes errors of a scan, each with the fix of the check which has detected it. The errors of a node are fixed together,
 * since they can block each other: a node is saved only once all its mandatory properties are set.
 * Keeps the state of each fix, keyed by error ID, until another scan is displayed.
 *
 * The values of an error are typed by the administrator: a rejected value is not a failed fix, so the previous state of
 * this error comes back, to let the administrator correct the value, and the reason is returned.
 */
export const useFixError = (resultsId, onFixed) => {
    const client = useApolloClient();
    const [states, setStates] = useState({});

    useEffect(() => {
        setStates({});
    }, [resultsId]);

    const update = changes => setStates(prev => {
        const next = {...prev};
        Object.entries(changes).forEach(([id, state]) => {
            if (state) {
                next[id] = state;
            } else {
                delete next[id];
            }
        });
        return next;
    });

    /**
     * @param fixes the errors to fix, as {id, values}: values is null for an error fixed without values
     * @return a promise of {fixed, message, errors}: fixed tells if all the errors have been fixed, errors gives the outcome of
     * each one, {id, fixed, fixFailureMessage}, and message why the fix has been refused, when no error is fixed
     */
    const fix = useCallback(fixes => {
        const previous = Object.fromEntries(fixes.map(({id}) => [id, states[id] === FIX_STATES.FIXING ? undefined : states[id]]));
        update(Object.fromEntries(fixes.map(({id}) => [id, FIX_STATES.FIXING])));
        return client.mutate({
            mutation: FIX_ERRORS,
            variables: {resultsID: resultsId, ids: fixes.map(f => f.id), values: fixes.map(f => f.values || null)},
            fetchPolicy: 'no-cache'
        })
            .then(({data, errors}) => {
                if (errors?.length) {
                    throw new Error(errors.map(e => e.message).join(', '));
                }

                const fixedIds = new Set((data?.integrity?.results?.errors || []).filter(e => e.fixed).map(e => e.id));
                update(Object.fromEntries(fixes.map(({id, values}) => [id, fixedIds.has(id) ? FIX_STATES.FIXED : (values ? previous[id] : FIX_STATES.FAILED)])));
                if (fixedIds.size > 0 && onFixed) {
                    onFixed();
                }

                return {fixed: fixedIds.size === fixes.length, errors: data?.integrity?.results?.errors || []};
            })
            .catch(e => {
                console.error('Impossible to fix the errors', fixes.map(f => f.id), e);
                // A rejected value stops the fix before the errors fixed without values: all of them are left as they were
                update(previous);
                // The errors fixed before the failure are stored as fixed: the results are read again
                if (onFixed) {
                    onFixed();
                }

                return {fixed: false, message: errorMessage(e)};
            });
    }, [client, resultsId, onFixed, states]);

    // The errors whose fix has failed during a fix all: the ones fixed since keep their state
    const markFailed = useCallback(ids => setStates(prev => {
        const next = {...prev};
        (ids || []).forEach(id => {
            if (next[id] !== FIX_STATES.FIXED) {
                next[id] = FIX_STATES.FAILED;
            }
        });
        return next;
    }), []);

    return {fix, states, markFailed};
};
