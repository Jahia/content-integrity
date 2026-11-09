import {defineConfig} from 'cypress';
import base from './cypress.config';

// The configuration of demo/screenshots.sh: the screenshots of the documentation of the checks, taken in a headless
// Chrome whose window holds the whole viewport, with a scale factor of 1 whatever the screen
const WIDTH = 1600;
const HEIGHT = 1000;

export default defineConfig({
    ...base,
    viewportWidth: WIDTH,
    viewportHeight: HEIGHT,
    e2e: {
        ...base.e2e,
        specPattern: 'demo/*.cy.ts',
        setupNodeEvents(on, config) {
            on('before:browser:launch', (browser, options) => {
                if (browser.family === 'chromium') {
                    options.args.push(`--window-size=${WIDTH},${HEIGHT}`, '--force-device-scale-factor=1');
                }

                return options;
            });
            return base.e2e.setupNodeEvents(on, config);
        }
    }
});
