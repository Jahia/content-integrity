import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {clearFilters, getDialog, getFilters, getResultsTable, getRow, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiResultsTable';
const CHECKS = ['JCRLanguagePropertyCheck', 'LockSanityCheck', 'PagesSanityCheck'];
const MISSING_LANGUAGE = `/sites/${SITE}/contents/jcr-language/missing-language/j:translation_en`;
const INCONSISTENT_LANGUAGE = `/sites/${SITE}/contents/jcr-language/inconsistent-language/j:translation_en`;
const MISSING_TEMPLATE = `/sites/${SITE}/home/missing-template`;
const INCONSISTENT_LOCK = `/sites/${SITE}/contents/locks/inconsistent-lock`;

/**
 * The language and template errors block an XML import, the lock errors do not.
 */
describe('Results table', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/JCRLanguagePropertyCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    // The page displays the latest scan results
    beforeEach(() => {
        scan(`/sites/${SITE}`, CHECKS);
        visitAdmin();
    });

    it('displays the check name, the error type, the workspace, the path and the message by default', () => {
        getResultsTable().find('thead th').then(cells => {
            // The last column holds the menu of the actions on each error
            const labels = cells.toArray().map(c => c.innerText.trim());
            expect(labels).to.deep.equal(['Check name', 'Error type', 'Workspace', 'Path', 'Message', 'Actions']);
        });
    });

    it('always displays the filters, and displays only the errors which block an XML import by default', () => {
        getFilters().within(() => {
            cy.contains('label', 'Impact on XML import').should('be.visible');
            cy.contains('true (3)').should('be.visible');
        });
        cy.contains(/^Errors: 3 \(total: \d+\)$/).should('be.visible');
        getRow(MISSING_LANGUAGE).should('exist');
        getRow(MISSING_TEMPLATE).should('exist');
        getResultsTable().find(`[title="${INCONSISTENT_LOCK}"]`).should('not.exist');

        clearFilters();
        cy.contains(/^Errors: \d+$/).should('be.visible');
        getRow(INCONSISTENT_LOCK).should('exist');
    });

    it('does not fix anything when the fix of all the errors is cancelled', () => {
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => {
            cy.contains('3 errors matching the filters').should('be.visible');
            cy.contains('button', 'Cancel').click();
        });
        cy.get('[role=dialog]').should('not.exist');
        cy.get('[role=status]').should('not.exist');
        getResultsTable().contains('Fixed').should('not.exist');
    });

    it('fixes all the errors matching the filters, and skips the ones its check can not fix', () => {
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => cy.contains('button', 'Fix 3 errors').click());
        cy.contains('[role=status]', '2 fixed, 0 not fixed, 1 skipped, 0 already fixed', {timeout: 30000}).should('be.visible');
        getRow(MISSING_LANGUAGE).within(() => cy.contains('Fixed').should('be.visible'));
        getRow(INCONSISTENT_LANGUAGE).within(() => cy.contains('Fixed').should('be.visible'));
        getRow(MISSING_TEMPLATE).within(() => cy.contains('Fixed').should('not.exist'));

        // The errors hidden by the filters are left as they are
        scan(`/sites/${SITE}`, CHECKS).then(results => {
            const types = results.errors.map(e => e.errorType);
            expect(types).to.not.include('MISSING_JCR_LANGUAGE_PROP');
            expect(types).to.not.include('INCONSISTENT_JCR_LANGUAGE_PROP');
            expect(types).to.include('MISSING_TEMPLATE');
            expect(types).to.include('INCONSISTENT_LOCK');
        });
    });
});
