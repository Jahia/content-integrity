// eslint-disable-next-line @typescript-eslint/no-require-imports
require('@jahia/cypress/dist/support/registerSupport').registerSupport();

Cypress.on('uncaught:exception', () => {
    // The Jahia administration may raise errors unrelated to the module under test
    return false;
});
