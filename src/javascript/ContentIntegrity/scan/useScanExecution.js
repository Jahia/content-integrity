import {useCallback, useEffect, useRef, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {GET_CURRENT_SCAN, GET_SCAN, RUN_SCAN, STOP_SCAN} from '../ContentIntegrity.gql';

export const RUNNING = 'running';
const FAILED = 'failed';
const POLL_INTERVAL_MS = 3000;

const runQuery = (client, query, variables) => client.query({query, variables, fetchPolicy: 'no-cache'})
    .then(({data, errors}) => {
        if (errors?.length) {
            throw new Error(errors.map(e => e.message).join(', '));
        }

        return data;
    });

/**
 * Tracks one scan execution: start, stop, and the polling of its logs. On mount it attaches to the scan
 * that is running, or else to the last one, because a scan runs in the background and outlives the page.
 */
export const useScanExecution = () => {
    const client = useApolloClient();
    const [execution, setExecution] = useState({id: null, status: null, startDate: null, logs: [], resultsID: null});
    const [error, setError] = useState(null);
    const [isStarting, setStarting] = useState(false);
    const timer = useRef(null);
    const previousStatus = useRef(null);
    const mounted = useRef(true);

    const stopPolling = useCallback(() => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }
    }, []);

    const poll = useCallback(id => {
        stopPolling();
        runQuery(client, GET_SCAN, {id})
            .then(data => {
                if (!mounted.current) {
                    return;
                }

                const scan = data?.integrity?.scan;
                if (!scan) {
                    return;
                }

                setExecution({
                    id: scan.id,
                    status: scan.status,
                    startDate: scan.startDate,
                    logs: scan.logs || [],
                    resultsID: scan.resultsID
                });
                if (scan.status === RUNNING) {
                    timer.current = setTimeout(() => poll(id), POLL_INTERVAL_MS);
                } else if (scan.status === FAILED && previousStatus.current === RUNNING) {
                    // The scan card is gone once the scan is over: its last log line explains the failure
                    setError(scan.logs?.[scan.logs.length - 1] || scan.status);
                }

                previousStatus.current = scan.status;
            })
            .catch(e => mounted.current && setError(e.message));
    }, [client, stopPolling]);

    useEffect(() => {
        mounted.current = true;
        runQuery(client, GET_CURRENT_SCAN)
            .then(data => {
                // The API returns the running scan, or else the last one: follow it, or show its outcome.
                const scan = data?.integrity?.scan;
                if (scan?.id) {
                    poll(scan.id);
                }
            })
            .catch(e => mounted.current && setError(e.message));
        return () => {
            mounted.current = false;
            stopPolling();
        };
    }, [client, poll, stopPolling]);

    const start = useCallback(({rootPath, excludedPaths, workspace, includeVirtualNodes, checks}) => {
        setError(null);
        setStarting(true);
        runQuery(client, RUN_SCAN, {
            path: rootPath,
            excludedPaths,
            ws: workspace,
            checks,
            skipMP: !includeVirtualNodes
        })
            .then(data => {
                const id = data?.integrity?.scan?.id;
                if (!id) {
                    throw new Error('The scan did not start');
                }

                setExecution({id, status: RUNNING, startDate: null, logs: [], resultsID: null});
                previousStatus.current = RUNNING;
                poll(id);
            })
            .catch(e => setError(e.message))
            .finally(() => mounted.current && setStarting(false));
    }, [client, poll]);

    const stop = useCallback(() => {
        if (!execution.id) {
            return;
        }

        runQuery(client, STOP_SCAN, {id: execution.id})
            .then(() => poll(execution.id))
            .catch(e => setError(e.message));
    }, [client, execution.id, poll]);

    return {execution, isRunning: execution.status === RUNNING, isStarting, error, start, stop};
};
