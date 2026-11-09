import {visitAdmin} from '../../support/adminPage';

describe('Labels of the administration page', () => {
    it('loads the labels under a versioned URL, so that an upgrade of the module is not hidden by the browser cache', () => {
        cy.intercept('GET', '**/modules/content-integrity/javascript/locales/*').as('locale');
        visitAdmin();
        cy.wait('@locale').its('request.url').should('match', /\/locales\/en\.v[0-9a-f]{32}\.json$/);
        // The labels are resolved: the button shows its English label, not its key
        cy.contains('button', 'New scan').should('be.visible');
    });
});
