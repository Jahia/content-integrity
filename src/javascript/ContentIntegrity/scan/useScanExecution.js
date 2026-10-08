import {useCallback, useEffect, useRef, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {GET_CURRENT_SCAN, GET_SCAN, RUN_SCAN, STOP_SCAN} from '../ContentIntegrity.gql';
import {subscribeToScan} from './scanSubscription';

export const RUNNING = 'running';
const FAILED = 'failed';
const POLL_INTERVAL_MS = 3000;
// As the server does for the logs it returns: the first lines, then the last ones
const LOGS_HEAD_SIZE = 100;
const LOGS_TAIL_SIZE = 500;
const LOGS_SKIPPED = '[...]';

const appendLogs = (logs, newLines) => {
    const all = logs.concat(newLines);
    return all.length <= LOGS_HEAD_SIZE + LOGS_TAIL_SIZE + 1 ? all : [...all.slice(0, LOGS_HEAD_SIZE), LOGS_SKIPPED, ...all.slice(-LOGS_TAIL_SIZE)];
};

const runQuery = (client, query, variables) => client.query({query, variables, fetchPolicy: 'no-cache'})
    .then(({data, errors}) => {
        if (errors?.length) {
            throw new Error(errors.map(e => e.message).join(', '));
        }

        return data;
    });

/**
 * Tracks one scan execution: start, stop, and its logs. On mount it attaches to the scan that is running, or else to
 * the last one, because a scan runs in the background and outlives the page.
 * The scan is followed through the GraphQL subscription contentIntegrityScan, which pushes its new log lines. When the
 * WebSocket connection fails, for example behind a proxy which does not let it through, the scan is polled instead.
 */
export const useScanExecution = () => {
    const client = useApolloClient();
    const [execution, setExecution] = useState({id: null, status: null, startDate: null, logs: [], resultsID: null});
    const [error, setError] = useState(null);
    const [isStarting, setStarting] = useState(false);
    const timer = useRef(null);
    const unsubscribe = useRef(null);
    const previousStatus = useRef(null);
    const mounted = useRef(true);

    const stopFollowing = useCallback(() => {
        if (timer.current) {
            clearTimeout(timer.current);
            timer.current = null;
        }

        if (unsubscribe.current) {
            unsubscribe.current();
            unsubscribe.current = null;
        }
    }, []);

    // The scan card is gone once the scan is over: when it fails, its last log line explains why
    const onStatus = useCallback((status, logs) => {
        if (status === FAILED && previousStatus.current === RUNNING) {
            setError(logs[logs.length - 1] || status);
        }

        previousStatus.current = status;
    }, []);

    const poll = useCallback(id => {
        stopFollowing();
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
                }

                onStatus(scan.status, scan.logs || []);
            })
            .catch(e => mounted.current && setError(e.message));
    }, [client, stopFollowing, onStatus]);

    const follow = useCallback(id => {
        stopFollowing();
        let isFirstEvent = true;
        let logs = [];
        unsubscribe.current = subscribeToScan(id, {
            onEvent: progress => {
                if (!mounted.current) {
                    return;
                }

                // The first event carries the lines written so far, the next ones only the new lines
                logs = isFirstEvent ? progress.logs : appendLogs(logs, progress.logs);
                isFirstEvent = false;
                setExecution({
                    id: progress.id,
                    status: progress.status,
                    startDate: progress.startDate,
                    logs,
                    resultsID: progress.resultsID
                });
                onStatus(progress.status, logs);
            },
            onComplete: () => {
                unsubscribe.current = null;
            },
            onError: () => {
                unsubscribe.current = null;
                if (mounted.current) {
                    poll(id);
                }
            }
        });
    }, [stopFollowing, poll, onStatus]);

    useEffect(() => {
        mounted.current = true;
        runQuery(client, GET_CURRENT_SCAN)
            .then(data => {
                // The API returns the running scan, or else the last one: follow it, or show its outcome.
                const scan = data?.integrity?.scan;
                if (scan?.id) {
                    follow(scan.id);
                }
            })
            .catch(e => mounted.current && setError(e.message));
        return () => {
            mounted.current = false;
            stopFollowing();
        };
    }, [client, follow, stopFollowing]);

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
                follow(id);
            })
            .catch(e => setError(e.message))
            .finally(() => mounted.current && setStarting(false));
    }, [client, follow]);

    const stop = useCallback(() => {
        if (!execution.id) {
            return;
        }

        // The subscription delivers the end of the scan. Without one, the scan is polled
        runQuery(client, STOP_SCAN, {id: execution.id})
            .then(() => !unsubscribe.current && poll(execution.id))
            .catch(e => setError(e.message));
    }, [client, execution.id, poll]);

    return {execution, isRunning: execution.status === RUNNING, isStarting, error, start, stop};
};
