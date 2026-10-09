import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {clearFilters, closeMenu, getColumnLabels, getDialog, getDropdown, getFilters, getMenuItem, getResultsTable, getRow, getScanLabel, openDropdown, selectInDropdown, visitAdmin} from '../../support/adminPage';

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

    let resultsId: string;

    // The page displays the latest scan results
    beforeEach(() => {
        scan(`/sites/${SITE}`, CHECKS).then(results => {
            resultsId = results.resultsId;
        });
        visitAdmin();
    });

    it('displays the check name, the error type, the workspace, the path and the message by default', () => {
        getResultsTable().find('thead th').then(cells => {
            // The first column holds the outcome of the fix of each error, its label is read by screen readers only
            const labels = cells.toArray().map(c => c.textContent.trim());
            expect(labels).to.deep.equal(['Fix status', 'Check name', 'Error type', 'Workspace', 'Path', 'Message']);
        });
        getScanLabel(resultsId).then(label => getDropdown('Scan').should('contain.text', label));
    });

    it('adds and removes columns, which keep their order', () => {
        // The menu stays open, so that several columns are toggled at once
        openDropdown('Columns');
        getMenuItem('Site').click();
        getMenuItem('Primary type').click();
        // A displayed column is removed from the menu, or from its tag
        getMenuItem('Workspace').click();
        closeMenu();
        getDropdown('Columns').contains('button', 'Message').click();
        getColumnLabels().should('deep.equal', ['Check name', 'Error type', 'Site', 'Path', 'Primary type']);
        getRow(MISSING_TEMPLATE).should('contain.text', SITE).and('contain.text', 'jnt:page');
    });

    it('filters the errors on the value of a column, and counts the errors of each value', () => {
        selectInDropdown('Check name', 'PagesSanityCheck (1)');
        cy.contains(/^Errors: 1 \(total: \d+\)$/).should('be.visible');
        getRow(MISSING_TEMPLATE).should('exist');
        getResultsTable().find(`[title="${MISSING_LANGUAGE}"]`).should('not.exist');
        // The values of the other columns are counted on the filtered errors
        openDropdown('Error type');
        getMenuItem('MISSING_TEMPLATE (1)');
        getMenuItem('MISSING_JCR_LANGUAGE_PROP (0)');
        closeMenu();

        clearFilters();
        getDropdown('Check name').should('contain.text', 'All');
        getDropdown('Impact on XML import').should('contain.text', 'All');
    });

    it('lists the results of a new scan once the page is refreshed', () => {
        getResultsTable().should('be.visible');
        openDropdown('Scan');
        cy.get('li.moonstone-menuItem').its('length').then(listed => {
            closeMenu();
            scan(`/sites/${SITE}/contents/locks`, ['LockSanityCheck']).then(results => {
                // The page lists the new results only once refreshed
                openDropdown('Scan');
                cy.get('li.moonstone-menuItem').should('have.length', listed);
                closeMenu();
                cy.contains('button', 'Refresh').click();
                openDropdown('Scan');
                cy.get('li.moonstone-menuItem').should('have.length', listed + 1);
                closeMenu();
                // The displayed results stay selected. Both labels are read once the new results exist: two scans
                // started within the same second are told apart by their milliseconds
                getScanLabel(resultsId).then(displayed => getDropdown('Scan').should('contain.text', displayed));
                getScanLabel(results.resultsId).then(added => {
                    selectInDropdown('Scan', added);
                    getDropdown('Scan').should('contain.text', added);
                });
                // Other results start from their own default filters: no lock error blocks an XML import, so none applies
                getDropdown('Impact on XML import').should('contain.text', 'All');
                getRow(INCONSISTENT_LOCK).should('exist');
            });
        });
    });

    it('opens the node of an error in the JCR browser', () => {
        // The JCR browser opens in a new window, with a tool access token issued for the current user
        const popup = {location: {href: ''}, opener: {}, close: cy.stub()};
        cy.window().then(win => {
            cy.stub(win, 'open').as('open').returns(popup);
        });
        getRow(MISSING_TEMPLATE).find('button[title="Open in the JCR browser"]').first().click();
        cy.get('@open').should('have.been.calledWith', 'about:blank', '_blank');
        cy.wrap(popup.location).its('href').should('match', /\/modules\/tools\/jcrBrowser\.jsp\?workspace=default&uuid=[0-9a-f-]+&toolAccessToken=.+/)
            .then(href => cy.request(href).its('body').should('contain', 'missing-template'));
        cy.wrap(popup.close).should('not.have.been.called');
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
