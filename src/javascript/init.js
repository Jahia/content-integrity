import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import register from './ContentIntegrity/register';

/* global CONTENT_INTEGRITY_LOCALE_FILES */
// Declares the checksums of the locale files, computed by the build, so that the app-shell loads them under a versioned URL
const declareLocaleFiles = () => {
    window.jahia = window.jahia || {};
    window.jahia.localeFiles = {...window.jahia.localeFiles, 'content-integrity': CONTENT_INTEGRITY_LOCALE_FILES};
};

export default function () {
    registry.add('callback', 'content-integrity', {
        targets: ['jahiaApp-init:60'],
        callback: async () => {
            declareLocaleFiles();
            await i18next.loadNamespaces('content-integrity');
            register();
        }
    });
}
