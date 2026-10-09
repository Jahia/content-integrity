import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {clearFilters, closeDetailsPanel, getDetailsPanel, getDialog, getResultsTable, getRow, openErrorDetails, selectInDropdown, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiFixErrors';
const LOCKS = `/sites/${SITE}/contents/locks`;
const MISSING_TEMPLATE = `/sites/${SITE}/home/missing-template`;
// A value which does not match the type of its definition: its check can't guess the right one, so its fix fails
const INVALID_VALUES = `/sites/${SITE}/contents/property-definitions/invalid-values`;
const CHECKS = ['LockSanityCheck', 'PagesSanityCheck', 'PropertyDefinitionsSanityCheck'];

describe('Fix of the errors', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    // The page displays the latest scan results. The lock errors do not block an XML import, so the default filter hides them
    beforeEach(() => {
        scan(`/sites/${SITE}`, CHECKS);
        visitAdmin();
        clearFilters();
    });

    it('opens the details of an error on a click on its row, and offers no menu', () => {
        getResultsTable().find('button[aria-label="Actions"]').should('not.exist');
        openErrorDetails(MISSING_TEMPLATE);
        getRow(MISSING_TEMPLATE).should('have.class', 'moonstone-TableRow-selected');
        getDetailsPanel().within(() => {
            cy.contains('Missing template').should('be.visible');
            cy.contains('MISSING_TEMPLATE').should('be.visible');
            cy.contains('PagesSanityCheck').should('be.visible');
            cy.contains('ci-template-which-does-not-exist').should('be.visible');
            // The check provides no fix for this error
            cy.contains('button', /^Fix/).should('not.exist');
        });
        // A click on another row displays the details of its error in the same panel
        openErrorDetails(`${LOCKS}/inconsistent-lock`);
        getDetailsPanel().should('contain.text', 'INCONSISTENT_LOCK').and('not.contain.text', 'MISSING_TEMPLATE');
        closeDetailsPanel();
    });

    it('closes the details with Escape, and gives the focus back to the row', () => {
        openErrorDetails(MISSING_TEMPLATE);
        cy.get('body').type('{esc}');
        cy.get('#ci-error-details').should('not.exist');
        getRow(MISSING_TEMPLATE).should('have.focus');
    });

    it('opens the details from the keyboard', () => {
        getRow(MISSING_TEMPLATE).focus().type('{enter}');
        getDetailsPanel().should('contain.text', MISSING_TEMPLATE);
    });

    it('fixes an error from its details, and displays the outcome in its row', () => {
        openErrorDetails(`${LOCKS}/inconsistent-lock`);
        getDetailsPanel().within(() => {
            cy.contains('button', /^Fix$/).click();
            cy.contains('Fixed').should('be.visible');
            cy.contains('button', /^Fix$/).should('not.exist');
        });
        closeDetailsPanel();
        getRow(`${LOCKS}/inconsistent-lock`).within(() => cy.contains('Fixed').should('be.visible'));
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            expect(results.errors.filter(e => e.nodePath === `${LOCKS}/inconsistent-lock`)).to.have.length(0);
        });
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('tags the errors that a fix all has not fixed', () => {
        selectInDropdown('Error type', /^INVALID_VALUE_TYPE \(1\)$/);
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => cy.contains('button', 'Fix 1 errors').click());
        cy.contains('[role=status]', '0 fixed, 1 not fixed, 0 skipped, 0 already fixed', {timeout: 30000}).should('be.visible');
        getRow(INVALID_VALUES).within(() => cy.contains('Not fixed').should('be.visible'));
        openErrorDetails(INVALID_VALUES);
        getDetailsPanel().within(() => {
            cy.contains('Not fixed').should('be.visible');
            cy.contains('The fix of the check has not fixed this error').should('be.visible');
        });
    });

    it('keeps the fixed status of an error once the page is reloaded', () => {
        openErrorDetails(`${LOCKS}/deletion-lock-on-translation/j:translation_en`);
        getDetailsPanel().contains('button', /^Fix$/).click();
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => cy.contains('Fixed').should('be.visible'));
        cy.reload();
        clearFilters();
        getRow(`${LOCKS}/deletion-lock-on-translation/j:translation_en`).within(() => {
            cy.contains('Fixed').should('be.visible');
        });
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });
});
