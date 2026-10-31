import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {defineConfig} from 'vite';
import {federation} from '@module-federation/vite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MF_NAME = 'contentIntegrity';
const outDir = path.resolve(__dirname, 'src/main/resources/javascript/apps');

// The module runs on Jahia 8.1.5 and later. The shared versions, measured on the jahia-ui-root and app-shell bundles:
// Jahia 8.1.5 shares React 16.14, Moonstone 1.6.2, i18next 19.9 and react-i18next 11.16 to 11.12;
// Jahia 8.2.3 shares React 18.3, Moonstone 2.14 and react-i18next 11.18.
// They are singletons provided by the running Jahia (import: false), never bundled here. Jahia 8.1 shares react-apollo 3
// for its UI and Jahia 8.2 @apollo/client 3: the module bundles @apollo/client and provides its own client
// (src/javascript/ContentIntegrity/common/apolloClient.js).
const hostShared = {
    react: '>=16.14.0 <19',
    'react-dom': '>=16.14.0 <19',
    '@jahia/moonstone': '>=1.6.2 <3',
    '@jahia/ui-extender': '^1.0.0',
    i18next: '>=19.9.2 <22',
    'react-i18next': '>=11.12.0 <12'
};
const shared = Object.fromEntries(Object.entries(hostShared).map(([name, requiredVersion]) => [name, {
    singleton: true,
    requiredVersion,
    import: false
}]));

// The checksum of each locale file. The app-shell requests a namespace listed in window.jahia.localeFiles as
// locales/<lang>.v<checksum>.json, which its URL rewriting serves as locales/<lang>.json: an upgrade of the module
// changes the URL, so a browser never keeps the labels of the previous version in its cache.
const localesDir = path.resolve(__dirname, 'src/main/resources/javascript/locales');
const localeFiles = Object.fromEntries(fs.readdirSync(localesDir)
    .filter(file => file.endsWith('.json'))
    .map(file => [file, crypto.createHash('md5').update(fs.readFileSync(path.join(localesDir, file))).digest('hex')]));

// Jahia loads javascript/apps/remoteEntry.js (package.json, jahia.remotes) with a classic <script>, and the app-shell
// reads the container from window.appShell.remotes.<name>. The federation plugin only emits an ES module entry: this
// script registers a container whose init() and get() import it. Its name is hashed, since the URL of remoteEntry.js
// is versioned by Jahia but the URLs of the files it imports are not.
const appShellRemoteEntry = () => ({
    name: 'content-integrity-app-shell-remote-entry',
    enforce: 'post',
    generateBundle(_options, bundle) {
        const esmEntry = Object.values(bundle).find(file => file.type === 'chunk' && file.name === 'remoteEntry');
        if (!esmEntry) {
            this.error('No remoteEntry chunk found in the bundle');
        }

        this.emitFile({
            type: 'asset',
            fileName: 'remoteEntry.js',
            source: `(function () {
    var base = document.currentScript.src.replace(/\\/[^/]*$/, '/');
    var load = function () {
        return import(base + ${JSON.stringify(esmEntry.fileName)});
    };
    window.appShell.remotes[${JSON.stringify(MF_NAME)}] = {
        init: function (shareScope, initScope) {
            return load().then(function (m) {
                return m.init(shareScope, initScope);
            });
        },
        get: function (module) {
            return load().then(function (m) {
                return m.get(module);
            });
        }
    };
})();
`
        });
    }
});

export default defineConfig(({mode}) => ({
    base: './',
    publicDir: false,
    define: {
        CONTENT_INTEGRITY_LOCALE_FILES: JSON.stringify(localeFiles)
    },
    css: {
        modules: {
            generateScopedName: 'ci_[local]'
        }
    },
    oxc: {
        jsx: {runtime: 'automatic'}
    },
    plugins: [
        federation({
            name: MF_NAME,
            filename: 'remoteEntry-[hash].js',
            exposes: {
                './init': './src/javascript/init.js'
            },
            shared,
            dts: false,
            manifest: false
        }),
        appShellRemoteEntry(),
        {
            name: 'content-integrity-copy-package-json',
            generateBundle() {
                this.emitFile({type: 'asset', fileName: 'package.json', source: fs.readFileSync(path.resolve(__dirname, 'package.json'))});
            }
        }
    ],
    build: {
        outDir,
        emptyOutDir: true,
        // Jahia 8.1.5 and later run in the browsers which load ES modules with import() and import.meta
        target: ['chrome64', 'edge79', 'firefox67', 'safari12'],
        sourcemap: mode === 'production',
        minify: mode === 'production',
        rollupOptions: {
            input: {}
        }
    }
}));
