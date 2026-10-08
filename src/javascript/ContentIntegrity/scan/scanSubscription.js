const contextPath = window.contextJsParameters?.contextPath || '';

// The protocol of subscriptions-transport-ws. The endpoint of Jahia 8.1.5 and 8.2 also announces graphql-transport-ws,
// but closes the connection on its messages: only this one works on both.
const PROTOCOL = 'graphql-ws';
const SUBSCRIPTION_ID = '1';
const QUERY = `subscription ContentIntegrityScanSubscription($id: String!) {
    contentIntegrityScan(id: $id) {
        id
        status
        startDate
        resultsID
        logs
    }
}`;

const errorOf = payload => new Error((payload?.errors || [payload]).map(e => e?.message || String(e)).join(', '));

/**
 * Follows a scan over the GraphQL WebSocket endpoint, without a client library: the module would otherwise bundle
 * subscriptions-transport-ws, which is no longer maintained, for these few messages.
 * - onEvent(progress) receives each event of the subscription contentIntegrityScan;
 * - onComplete() is called when the server ends the subscription, after the event of the end of the scan;
 * - onError(error) is called when the connection fails or the server refuses the subscription.
 * Returns the function which stops following the scan.
 */
export const subscribeToScan = (id, {onEvent, onComplete, onError}) => {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    let socket;
    try {
        socket = new WebSocket(`${scheme}://${window.location.host}${contextPath}/modules/graphqlws`, PROTOCOL);
    } catch (e) {
        onError(e);
        return () => {};
    }

    let isOver = false;
    const end = callback => {
        if (!isOver) {
            isOver = true;
            callback();
        }
    };

    socket.onopen = () => socket.send(JSON.stringify({type: 'connection_init', payload: {}}));
    socket.onmessage = message => {
        let data;
        try {
            data = JSON.parse(message.data);
        } catch (e) {
            return;
        }

        switch (data.type) {
            case 'connection_ack':
                socket.send(JSON.stringify({id: SUBSCRIPTION_ID, type: 'start', payload: {query: QUERY, variables: {id}}}));
                break;
            case 'data':
                if (data.payload?.errors?.length) {
                    end(() => onError(errorOf(data.payload)));
                    socket.close();
                } else if (data.payload?.data?.contentIntegrityScan) {
                    onEvent(data.payload.data.contentIntegrityScan);
                }

                break;
            case 'error':
            case 'connection_error':
                end(() => onError(errorOf(data.payload)));
                socket.close();
                break;
            case 'complete':
                end(onComplete);
                socket.close();
                break;
            default:
                // 'ka': the keep-alive of the server
                break;
        }
    };

    socket.onclose = () => end(() => onError(new Error('The connection to the server was closed')));

    return () => end(() => {
        if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({id: SUBSCRIPTION_ID, type: 'stop'}));
        }

        socket.close();
    });
};
