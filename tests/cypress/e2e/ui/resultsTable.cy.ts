import {comparePaths, createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {
    clearFilters,
    closeDetailsPanel,
    closeMenu,
    confirmFix,
    getColumnLabels,
    getDetailsPanel,
    getDialog,
    getDropdown,
    getFilters,
    getMenuItem,
    getNodeErrorRows,
    getNodeRow,
    getResultsTable,
    getRow,
    getScanLabel,
    openDropdown,
    openErrorDetails,
    selectInDropdown,
    visitLatestResults
} from '../../support/adminPage';

const SITE = 'ciUiResultsTable';
const CHECKS = ['JCRLanguagePropertyCheck', 'LockSanityCheck', 'PagesSanityCheck'];
const MISSING_LANGUAGE = `/sites/${SITE}/contents/jcr-language/missing-language/j:translation_en`;
const INCONSISTENT_LANGUAGE = `/sites/${SITE}/contents/jcr-language/inconsistent-language/j:translation_en`;
const MISSING_TEMPLATE = `/sites/${SITE}/home/missing-template`;
const INCONSISTENT_LOCK = `/sites/${SITE}/contents/locks/inconsistent-lock`;
// A translation node with two lock errors
const LOCKED_TRANSLATION = `/sites/${SITE}/contents/locks/deletion-lock-on-translation/j:translation_en`;

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
        visitLatestResults();
    });

    it('displays the path, the workspace, the check name, the error type and the message by default, as a tree of the nodes and their errors', () => {
        getResultsTable().find('thead th').then(cells => {
            // The last column holds the outcome of the fix of each error, its label is read by screen readers only
            const labels = cells.toArray().map(c => c.textContent.trim());
            expect(labels).to.deep.equal(['Path', 'Workspace', 'Check name', 'Error type', 'Message', 'Fix status']);
        });
        getNodeRow(MISSING_TEMPLATE).should('contain.text', 'default').and('contain.text', MISSING_TEMPLATE);
        getScanLabel(resultsId).then(label => getDropdown('Scan').should('contain.text', label));
        // Below the row of its node, each error repeats the columns of the node, such as its path and its workspace
        clearFilters();
        getNodeRow(LOCKED_TRANSLATION).should('not.have.attr', 'data-error-id');
        getNodeErrorRows(LOCKED_TRANSLATION).should('have.length.greaterThan', 1).each(row => {
            cy.wrap(row).find('td').eq(0).should('contain.text', LOCKED_TRANSLATION);
            cy.wrap(row).find('td').eq(1).should('have.text', 'default');
        });
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
        getColumnLabels().should('deep.equal', ['Path', 'Check name', 'Error type', 'Site', 'Primary type']);
        // The only error of a node is displayed in a single row, which tells the node as well
        getNodeRow(MISSING_TEMPLATE).should('have.attr', 'data-error-id');
        getRow(MISSING_TEMPLATE).should('contain.text', SITE).and('contain.text', 'jnt:page').and('contain.text', 'PagesSanityCheck');
        getRow(MISSING_TEMPLATE).find('[aria-expanded]').should('not.exist');
        // The row of a node with several errors displays only the columns of the node, the rows of its errors repeat them
        clearFilters();
        // An assertion on an attribute yields its value: the row is read again for its text
        getNodeRow(LOCKED_TRANSLATION).should('not.have.attr', 'data-error-id');
        getNodeRow(LOCKED_TRANSLATION).should('contain.text', 'jnt:translation').and('not.contain.text', 'LockSanityCheck');
        // The rows of its errors repeat the columns of the node, such as its path and its primary type
        getRow(LOCKED_TRANSLATION).should('contain.text', 'LockSanityCheck').and('contain.text', LOCKED_TRANSLATION).and('contain.text', 'jnt:translation');
    });

    it('filters the errors on the value of a column, and counts the errors of each value', () => {
        selectInDropdown('Check name', 'PagesSanityCheck (1)');
        cy.contains(/^Errors: 1 \(total: \d+\)$/).should('be.visible');
        getRow(MISSING_TEMPLATE).should('exist');
        getResultsTable().find(`tr[data-node-path="${MISSING_LANGUAGE}"]`).should('not.exist');
        // The values of the other columns are counted on the filtered errors: a value without error is not offered
        openDropdown('Error type');
        getMenuItem('MISSING_TEMPLATE (1)');
        cy.get('li.moonstone-menuItem').filter(':contains("MISSING_JCR_LANGUAGE_PROP")').should('not.exist');
        closeMenu();

        clearFilters();
        getDropdown('Check name').should('contain.text', 'All');
        getDropdown('Impact on XML import').should('contain.text', 'All');
    });

    it('clears the results once refreshed, without reloading the page, and lists the results of a new scan', () => {
        getResultsTable().should('be.visible');
        // A filter and an open panel, which the refresh clears
        clearFilters();
        selectInDropdown('Check name', /^PagesSanityCheck \(1\)$/);
        openErrorDetails(MISSING_TEMPLATE);
        // A reload of the page would lose this mark
        cy.window().then(win => {
            (win as any).ciNotReloaded = true;
        });
        openDropdown('Scan');
        cy.get('li.moonstone-menuItem').its('length').then(listed => {
            closeMenu();
            scan(`/sites/${SITE}/contents/locks`, ['LockSanityCheck']).then(results => {
                // The page lists the new results only once refreshed
                openDropdown('Scan');
                cy.get('li.moonstone-menuItem').should('have.length', listed);
                closeMenu();
                cy.contains('button', 'Refresh').click();
                // No results are displayed until a scan is chosen
                getDropdown('Scan').should('contain.text', 'Select a scan');
                cy.get('#ci-scan-status').should('not.exist');
                cy.get('#ci-filters').should('not.exist');
                getResultsTable().should('not.exist');
                cy.contains('No error found by this scan').should('not.exist');
                cy.get('#ci-node-errors').should('not.exist');
                cy.get('#ci-empty-state').should('contain.text', 'No scan selected');
                cy.window().its('ciNotReloaded').should('equal', true);
                openDropdown('Scan');
                cy.get('li.moonstone-menuItem').should('have.length', listed + 1);
                closeMenu();
                // The new results, with their default filters: no lock error blocks an XML import, so none applies. Both
                // labels are read once the new results exist: two scans started within the same second are told apart by
                // their milliseconds
                getScanLabel(results.resultsId).then(added => {
                    selectInDropdown('Scan', added);
                    getDropdown('Scan').should('contain.text', added);
                });
                getDropdown('Check name').should('contain.text', 'All');
                getDropdown('Impact on XML import').should('contain.text', 'All');
                getRow(INCONSISTENT_LOCK).should('exist');
                // The previous results are still listed
                getScanLabel(resultsId).then(previous => {
                    selectInDropdown('Scan', previous);
                    getDropdown('Scan').should('contain.text', previous);
                });
            });
        });
    });

    it('opens the node of an error in the JCR browser', () => {
        // The JCR browser opens in a new window, with a tool access token issued for the current user
        const popup = {location: {href: ''}, opener: {}, close: cy.stub()};
        cy.window().then(win => {
            cy.stub(win, 'open').as('open').returns(popup);
        });
        // The tooltip of the link tells the full path, then the action
        getNodeRow(MISSING_TEMPLATE).find('button').filter((i, button) => button.title === `${MISSING_TEMPLATE}\nOpen in the JCR browser`).first().click();
        cy.get('@open').should('have.been.calledWith', 'about:blank', '_blank');
        cy.wrap(popup.location).its('href').should('match', /\/modules\/tools\/jcrBrowser\.jsp\?workspace=default&uuid=[0-9a-f-]+&toolAccessToken=.+/)
            .then(href => cy.request(href).its('body').should('contain', 'missing-template'));
        cy.wrap(popup.close).should('not.have.been.called');
    });

    it('tells that the browser blocked the window of the JCR browser', () => {
        // A blocked popup: window.open returns null, and a click must not silently do nothing
        cy.window().then(win => {
            cy.stub(win, 'open').as('open').returns(null);
        });
        getNodeRow(MISSING_TEMPLATE).find('button').filter((i, button) => button.title === `${MISSING_TEMPLATE}\nOpen in the JCR browser`).first().click();
        cy.get('@open').should('have.been.calledWith', 'about:blank', '_blank');
        getNodeRow(MISSING_TEMPLATE).find('[role="alert"]').should('contain.text', 'The browser blocked the window of the JCR browser');
    });

    it('always displays the filters, and displays only the errors which block an XML import by default', () => {
        getFilters().within(() => {
            cy.contains('label', 'Impact on XML import').should('be.visible');
            cy.contains('true (3)').should('be.visible');
        });
        cy.contains(/^Errors: 3 \(total: \d+\)$/).should('be.visible');
        getRow(MISSING_LANGUAGE).should('exist');
        getRow(MISSING_TEMPLATE).should('exist');
        getResultsTable().find(`tr[data-node-path="${INCONSISTENT_LOCK}"]`).should('not.exist');

        clearFilters();
        cy.contains(/^Errors: \d+$/).should('be.visible');
        getRow(INCONSISTENT_LOCK).should('exist');
    });

    it('lists the errors of a node after the row of its path, sorted by workspace then by path', () => {
        clearFilters();
        getResultsTable().find('tbody tr').then(rows => {
            const keys: string[][] = [];
            let current: string[] = null;
            rows.toArray().forEach(row => {
                const key = [row.dataset.workspace, row.dataset.nodePath];
                if (row.dataset.nodeGroup !== undefined) {
                    current = key;
                    keys.push(key);
                } else {
                    // An error row belongs to the node of the row above it
                    expect(key, 'node of the error row').to.deep.equal(current);
                }
            });
            const sorted = [...keys].sort((a, b) => (a[0] === b[0] ? comparePaths(a[1], b[1]) : (a[0] < b[0] ? -1 : 1)));
            expect(keys).to.deep.equal(sorted);
            // A node starts once on the page
            expect(new Set(keys.map(k => k.join('|'))).size).to.equal(keys.length);
        });
    });

    it('collapses and expands the errors of a node', () => {
        clearFilters();
        getNodeErrorRows(LOCKED_TRANSLATION).should('have.length', 2);
        // The chevron is the icon of the cell: the rest of the cell is the link of the path
        getNodeRow(LOCKED_TRANSLATION).find('[aria-label="Hide the errors of the node"] > svg').click();
        getNodeErrorRows(LOCKED_TRANSLATION).should('have.length', 0);
        // A click on the chevron does not open the errors of the node
        cy.get('#ci-node-errors').should('not.exist');
        getNodeRow(LOCKED_TRANSLATION).find('[aria-label="Show the errors of the node"]').focus().type('{enter}');
        getNodeErrorRows(LOCKED_TRANSLATION).should('have.length', 2);
    });

    it('filters the errors fixed, skipped or not fixed, the three statuses being always offered', () => {
        clearFilters();
        // No fix has run yet: every error is skipped, and the other statuses are offered without error
        openDropdown('Fix status');
        cy.get('li.moonstone-menuItem').then(items => {
            const labels = items.toArray().map(item => item.textContent.trim());
            expect(labels[0]).to.equal('All');
            expect(labels[1]).to.equal('Fixed (0)');
            expect(labels[2]).to.match(/^Skipped \(\d+\)$/);
            expect(labels[3]).to.equal('Not fixed (0)');
        });
        closeMenu();
        openErrorDetails(INCONSISTENT_LOCK);
        getDetailsPanel().contains('button', /^Fix$/).click();
        confirmFix(1);
        getDetailsPanel().contains('Fixed').should('be.visible');
        closeDetailsPanel();
        selectInDropdown('Fix status', 'Fixed (1)');
        getRow(INCONSISTENT_LOCK).should('exist');
        getResultsTable().find(`tr[data-node-path="${MISSING_LANGUAGE}"]`).should('not.exist');
        selectInDropdown('Fix status', /^Skipped \(\d+\)$/);
        getResultsTable().find(`tr[data-node-path="${INCONSISTENT_LOCK}"]`).should('not.exist');
        getRow(MISSING_LANGUAGE).should('exist');
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('does not fix anything when the fix of all the errors is cancelled', () => {
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => {
            cy.contains('3 errors matching the filters').should('be.visible');
            // The fix is confirmed only once the administrator has ticked that the data are going to be altered
            cy.contains('Be careful, data are going to be altered in this environment').should('be.visible');
            cy.get('#ci-acknowledge').should('not.be.checked');
            cy.contains('button', 'Fix 3 errors').should('be.disabled');
            cy.get('#ci-acknowledge').click({force: true});
            cy.contains('button', 'Fix 3 errors').should('be.enabled');
            cy.contains('button', 'Cancel').click();
        });
        cy.get('[role=dialog]').should('not.exist');
        // Opened again, the confirmation has to be ticked again
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => {
            cy.get('#ci-acknowledge').should('not.be.checked');
            cy.contains('button', 'Fix 3 errors').should('be.disabled');
            cy.contains('button', 'Cancel').click();
        });
        cy.get('[role=dialog]').should('not.exist');
        getResultsTable().contains('Fixed').should('not.exist');
    });

    it('fixes all the errors matching the filters, and skips the ones fixed with a chosen value', () => {
        cy.contains('button', 'Fix all').click();
        confirmFix(3);
        // The outcome tells how many errors are fixed, and why the other ones are skipped
        getDialog('Outcome of the fix', 30000).within(() => {
            cy.get('[data-fix-outcome=fixed]').should('have.text', 'Fixed: 2');
            cy.get('[data-fix-outcome=skipped]').should('contain.text', 'Skipped: 1')
                .find('[data-reason=TAKES_VALUES]').should('have.text', '1 fixed with a value: open the panel of their node to type it');
            cy.get('[data-fix-outcome=failed]').should('have.text', 'Not fixed: 0');
            cy.contains('button', 'Close').click();
        });
        cy.get('[role=dialog]').should('not.exist');
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
