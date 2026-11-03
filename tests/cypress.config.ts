import {defineConfig} from 'cypress';

export default defineConfig({
    chromeWebSecurity: false,
    defaultCommandTimeout: 20000,
    requestTimeout: 60000,
    responseTimeout: 60000,
    pageLoadTimeout: 60000,
    screenshotsFolder: './results/screenshots',
    videosFolder: './results/videos',
    video: false,
    viewportWidth: 1366,
    viewportHeight: 768,
    watchForFileChanges: false,
    e2e: {
        specPattern: 'cypress/e2e/**/*.cy.ts',
        supportFile: 'cypress/support/e2e.ts',
        setupNodeEvents(on, config) {
            // eslint-disable-next-line @typescript-eslint/no-require-imports
            require('@jahia/cypress/dist/plugins/registerPlugins').registerPlugins(on, config);
            return config;
        }
    }
});
