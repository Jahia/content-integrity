import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {getDialog, getRow, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiFixErrors';
const LOCKS = `/sites/${SITE}/contents/locks`;
const MISSING_TEMPLATE = `/sites/${SITE}/home/missing-template`;

describe('Fix of the errors', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    // The page displays the latest scan results
    beforeEach(() => {
        scan(`/sites/${SITE}`, ['LockSanityCheck', 'PagesSanityCheck']);
        visitAdmin();
    });

    it('displays no fix button for an error that its check can not fix', () => {
        getRow(MISSING_TEMPLATE).within(() => {
            cy.contains('button', /^Fix/).should('not.exist');
        });
    });

    it('fixes an error from the results table', () => {
        getRow(`${LOCKS}/inconsistent-lock`).within(() => {
            cy.contains('button', /^Fix$/).click();
            cy.contains('Fixed').should('be.visible');
            cy.contains('button', /^Fix$/).should('not.exist');
        });
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            expect(results.errors.filter(e => e.nodePath === `${LOCKS}/inconsistent-lock`)).to.have.length(0);
        });
    });

    it('keeps the fixed status of an error once the page is reloaded', () => {
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => {
            cy.contains('button', /^Fix$/).first().click();
            cy.contains('Fixed').should('be.visible');
        });
        cy.reload();
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => {
            cy.contains('Fixed').should('be.visible');
        });
    });

    it('fixes an error from its details dialog', () => {
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        scan(`/sites/${SITE}`, ['LockSanityCheck', 'PagesSanityCheck']);
        cy.reload();
        getRow(`${LOCKS}/inconsistent-lock`).within(() => {
            cy.get('button[aria-label="Error details"]').click();
        });
        getDialog('Error details').within(() => {
            cy.contains('INCONSISTENT_LOCK').should('be.visible');
            cy.contains('LockSanityCheck').should('be.visible');
            cy.contains('button', /^Fix$/).click();
            cy.contains('Fixed').should('be.visible');
            cy.contains('button', 'Close').click();
        });
        getRow(`${LOCKS}/inconsistent-lock`).within(() => cy.contains('Fixed').should('be.visible'));
    });

    it('displays the details of an error', () => {
        getRow(MISSING_TEMPLATE).within(() => {
            cy.get('button[aria-label="Error details"]').click();
        });
        getDialog('Error details').within(() => {
            cy.contains('Missing template').should('be.visible');
            cy.contains(MISSING_TEMPLATE).should('be.visible');
            cy.contains('MISSING_TEMPLATE').should('be.visible');
            cy.contains('PagesSanityCheck').should('be.visible');
            cy.contains('ci-template-which-does-not-exist').should('be.visible');
            cy.contains('button', /^Fix/).should('not.exist');
            cy.contains('button', 'Close').click();
        });
    });
});
