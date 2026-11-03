import {ApolloClient, HttpLink, InMemoryCache} from '@apollo/client';

const contextPath = window.contextJsParameters?.contextPath || '';

/**
 * The module's own GraphQL client. Jahia 8.1 shares react-apollo 3 and Jahia 8.2 shares @apollo/client 3, so the host
 * client is not reachable the same way on both: the module bundles @apollo/client and provides this client itself.
 */
export const apolloClient = new ApolloClient({
    link: new HttpLink({uri: `${contextPath}/modules/graphql`, credentials: 'same-origin'}),
    cache: new InMemoryCache()
});
