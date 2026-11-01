import React from 'react';
import {ApolloProvider} from '@apollo/client';
import {registry} from '@jahia/ui-extender';
import {apolloClient} from './common/apolloClient';
import {ContentIntegrityAdmin} from './ContentIntegrityAdmin';

export default () => {
    registry.add('adminRoute', 'content-integrity', {
        targets: ['administration-server-systemHealth:999'],
        label: 'content-integrity:label.settings.title',
        isSelectable: true,
        requiredPermission: 'adminContentIntegrity',
        render: () => React.createElement(ApolloProvider, {client: apolloClient}, React.createElement(ContentIntegrityAdmin))
    });
};
