const path = require('path');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const {CleanWebpackPlugin} = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const ModuleFederationPlugin = require('webpack/lib/container/ModuleFederationPlugin');
const getModuleFederationConfig = require('@jahia/webpack-config/getModuleFederationConfig');
const packageJson = require('./package.json');

// The module runs on Jahia 8.1.5 and later. The shared versions, measured on the jahia-ui-root and app-shell bundles:
// Jahia 8.1.5 shares React 16.14, Moonstone 1.6.2, i18next 19.9 and react-i18next 11.16 to 11.12;
// Jahia 8.2.3 shares React 18.3, Moonstone 2.14 and react-i18next 11.18.
const moduleFederationConfig = getModuleFederationConfig(packageJson);
Object.entries({
    react: '>=16.14.0 <19',
    'react-dom': '>=16.14.0 <19',
    '@jahia/moonstone': '>=1.6.2 <3',
    i18next: '>=19.9.2 <22',
    'react-i18next': '>=11.12.0 <12'
}).forEach(([name, range]) => {
    moduleFederationConfig.shared[name].requiredVersion = range;
});
// Jahia 8.1 shares react-apollo 3 for its UI and Jahia 8.2 @apollo/client 3: the module bundles @apollo/client and
// provides its own client (src/javascript/ContentIntegrity/common/apolloClient.js).
delete moduleFederationConfig.shared['@apollo/client'];

module.exports = (env, argv) => {
    const config = {
        entry: {
            main: path.resolve(__dirname, 'src/javascript/index')
        },
        output: {
            path: path.resolve(__dirname, 'src/main/resources/javascript/apps/'),
            filename: 'content-integrity.bundle.js',
            chunkFilename: '[name].jahia.[chunkhash:6].js'
        },
        resolve: {
            mainFields: ['module', 'main'],
            extensions: ['.mjs', '.js', '.jsx', '.json', '.scss'],
            fallback: {url: false}
        },
        module: {
            rules: [
                {
                    test: /\.m?js$/,
                    type: 'javascript/auto'
                },
                {
                    test: /\.jsx?$/,
                    include: [path.join(__dirname, 'src/javascript')],
                    use: {
                        loader: 'babel-loader',
                        options: {
                            presets: [
                                ['@babel/preset-env', {
                                    modules: false,
                                    targets: {chrome: '60', edge: '44', firefox: '54', safari: '12'}
                                }],
                                '@babel/preset-react'
                            ],
                            plugins: ['@babel/plugin-syntax-dynamic-import']
                        }
                    }
                },
                {
                    test: /\.scss$/i,
                    sideEffects: true,
                    use: [
                        'style-loader',
                        {
                            loader: 'css-loader',
                            options: {modules: {localIdentName: 'ci_[local]'}}
                        },
                        'sass-loader'
                    ]
                }
            ]
        },
        plugins: [
            new CleanWebpackPlugin({verbose: false}),
            // React, Moonstone and i18next are shared singletons provided by the running Jahia (import: false),
            // never bundled here. Apollo is not: see moduleFederationConfig below.
            new ModuleFederationPlugin(moduleFederationConfig),
            new CopyWebpackPlugin({
                patterns: [
                    {from: 'package.json', to: ''}
                ]
            })
        ],
        mode: 'development'
    };

    config.devtool = (argv.mode === 'production') ? 'source-map' : 'eval-source-map';

    if (argv.analyze) {
        config.devtool = 'source-map';
        config.plugins.push(new BundleAnalyzerPlugin());
    }

    return config;
};
