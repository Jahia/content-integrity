import React from 'react';
import {registry} from '@jahia/ui-extender';
import {ContentIntegrityAdmin} from './ContentIntegrityAdmin';

export default () => {
    registry.add('adminRoute', 'content-integrity', {
        targets: ['administration-server-systemHealth:999'],
        label: 'content-integrity:label.settings.title',
        isSelectable: true,
        requiredPermission: 'adminContentIntegrity',
        render: () => React.createElement(ContentIntegrityAdmin)
    });
};
