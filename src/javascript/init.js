import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import register from './ContentIntegrity/register';

export default function () {
    registry.add('callback', 'content-integrity', {
        targets: ['jahiaApp-init:60'],
        callback: async () => {
            await i18next.loadNamespaces('content-integrity');
            register();
        }
    });
}
