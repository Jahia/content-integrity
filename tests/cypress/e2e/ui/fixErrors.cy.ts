import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {getDialog, getMenuItem, getRow, openRowMenu, visitAdmin} from '../../support/adminPage';

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

    it('displays no fix in the menu of an error that its check can not fix', () => {
        openRowMenu(MISSING_TEMPLATE);
        getMenuItem('Error details');
        cy.contains('li.moonstone-menuItem', /^Fix/).should('not.exist');
    });

    it('fixes an error from the menu of its row', () => {
        openRowMenu(`${LOCKS}/inconsistent-lock`);
        getMenuItem(/^Fix$/).click();
        getRow(`${LOCKS}/inconsistent-lock`).within(() => cy.contains('Fixed').should('be.visible'));
        openRowMenu(`${LOCKS}/inconsistent-lock`);
        getMenuItem('Error details');
        cy.contains('li.moonstone-menuItem', /^Fix$/).should('not.exist');
        cy.get('body').type('{esc}');
        cy.get('li.moonstone-menuItem').should('not.exist');
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            expect(results.errors.filter(e => e.nodePath === `${LOCKS}/inconsistent-lock`)).to.have.length(0);
        });
    });

    it('keeps the fixed status of an error once the page is reloaded', () => {
        openRowMenu(`${LOCKS}/deletion-lock-on-translation/j:translation_en`);
        getMenuItem(/^Fix$/).click();
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => cy.contains('Fixed').should('be.visible'));
        cy.reload();
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => {
            cy.contains('Fixed').should('be.visible');
        });
    });

    it('fixes an error from its details dialog', () => {
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        scan(`/sites/${SITE}`, ['LockSanityCheck', 'PagesSanityCheck']);
        cy.reload();
        openRowMenu(`${LOCKS}/inconsistent-lock`);
        getMenuItem('Error details').click();
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
        openRowMenu(MISSING_TEMPLATE);
        getMenuItem('Error details').click();
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
