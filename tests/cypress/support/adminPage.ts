import {graphql} from './integrity';

/**
 * The administration page of the module, and its dialogs. The labels are the English ones.
 */
export const ADMIN_URL = '/jahia/administration/content-integrity';

/**
 * Opens the administration page, as root by default.
 */
export const visitAdmin = (user?: { username: string; password: string }): void => {
    if (user) {
        cy.login(user.username, user.password);
    } else {
        cy.login();
    }

    cy.visit(ADMIN_URL);
    cy.contains('h1, h2, h3', 'Content Integrity', {timeout: 60000}).should('be.visible');
};

/**
 * Opens the administration page, which displays no results until a scan is chosen, then selects the latest results of a
 * scan which is over, as the specs which scan before opening the page expect.
 */
export const visitLatestResults = (user?: { username: string; password: string }): void => {
    // The results are read as root before the page is opened as the user: the requests of the page then run as the user
    graphql('{ integrity: contentIntegrity { scanResults: scanResultsSummaries { id status } } }').then(data => {
        const latest = [...data.integrity.scanResults].reverse().find((s: { status: string }) => s.status !== 'running');
        expect(latest, 'Results of a scan which is over').to.exist;
        getScanLabel(latest.id).then(label => {
            visitAdmin(user);
            selectInDropdown('Scan', label);
        });
    });
};

/**
 * Opens the confirmation of the deletion of all the results, from the menu after the title of the results card.
 */
export const openDeleteAll = (): void => {
    cy.get('#ci-results-menu').click();
    cy.contains('li.moonstone-menuItem', /^Delete all$/).click();
};

export const getDialog = (title: string, timeout?: number): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('[role=dialog]', title, timeout ? {timeout} : undefined).should('be.visible');

/**
 * Confirms a fix, of the errors of a node or of all the displayed errors: ticks that the data are going to be altered,
 * which enables the button. The confirmation is looked up in the whole page, whatever the scope of a within.
 */
export const confirmFix = (count: number): void => {
    cy.document().its('body').find('#ci-acknowledge').closest('[role=dialog]').should('be.visible').within(() => {
        cy.get('#ci-acknowledge').should('not.be.checked').click({force: true});
        cy.contains('button', `Fix ${count} errors`).should('be.enabled').click();
    });
};

/**
 * The card of the scan which runs. It is displayed only while a scan runs.
 */
export const getCurrentScanCard = (): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('#ci-exec-title', 'Scan in progress', {timeout: 30000}).closest('section');

export const getResultsTable = (): Cypress.Chainable<JQuery<HTMLElement>> => cy.get('[aria-label="Integrity errors"]', {timeout: 30000});

/**
 * The first row of an error of the node, among the rows displayed on the current page. The errors of a node follow the row
 * which tells its workspace and its path.
 */
export const getRow = (path: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    getResultsTable().find(`tr[data-error-id][data-node-path="${path}"]`).first();

/**
 * The rows of the errors of the node displayed on the current page.
 */
export const getNodeErrorRows = (path: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    getResultsTable().find(`tr[data-error-id][data-node-path="${path}"]`);

/**
 * The row which starts the errors of a node, with its workspace and its path.
 */
export const getNodeRow = (path: string, workspace = 'default'): Cypress.Chainable<JQuery<HTMLElement>> =>
    getResultsTable().find(`tr[data-node-group][data-node-path="${path}"][data-workspace="${workspace}"]`).first();

/**
 * The side panel which displays the errors of the node of the selected row, and their fix.
 */
export const getDetailsPanel = (): Cypress.Chainable<JQuery<HTMLElement>> => cy.get('#ci-node-errors').should('be.visible');

/**
 * Opens the errors of the node of a row, with a click on its left end: the row of an error has no chevron, and its path,
 * a link to the JCR browser, starts after an indent. Its right end, the fix status, may be under the side panel.
 */
/**
 * The workspace of the node displayed in the details panel, read from the details of the node.
 */
export const getDetailsWorkspace = (): Cypress.Chainable<JQuery<HTMLElement>> =>
    getDetailsPanel().contains('dt', /^Workspace$/).next('dd');

export const openErrorDetails = (path: string): void => {
    getRow(path).click('left');
    getDetailsPanel().should('contain.text', path);
};

export const closeDetailsPanel = (): void => {
    getDetailsPanel().find('button[aria-label="Close"]').click();
    cy.get('#ci-node-errors').should('not.exist');
};

/**
 * An item of an opened menu, such as the one of a dropdown. The menu is displayed above the page.
 */
export const getMenuItem = (label: string | RegExp): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('li.moonstone-menuItem', label).should('be.visible');

/**
 * The filters of the results table, which are always displayed.
 */
export const getFilters = (): Cypress.Chainable<JQuery<HTMLElement>> => cy.get('#ci-filters', {timeout: 30000}).should('be.visible');

/**
 * Removes the filters of the results table, including the default one, which displays only the errors which block an
 * XML import.
 */
export const clearFilters = (): void => {
    getFilters().within(() => cy.contains('button', 'Clear the filters').click());
    getFilters().contains('button', 'Clear the filters').should('not.exist');
};

/**
 * The Moonstone dropdown displayed under a label, such as a filter of the results table or a field of a dialog.
 * Its role is listbox in Moonstone 2 (Jahia 8.2) and dropdown in Moonstone 1.6 (Jahia 8.1.5), its class is the same.
 */
export const getDropdown = (label: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('label', new RegExp(`^${label}$`)).parent().find('.moonstone-dropdown').first();

/**
 * Opens the menu of a dropdown from its chevron, on its right: the middle of a multiple dropdown holds its tags, which
 * are buttons of their own.
 */
export const openDropdown = (label: string): void => {
    getDropdown(label).click('right');
};

/**
 * Closes the opened menu of a dropdown. Escape does not close it, a click outside of the menu does.
 */
export const closeMenu = (): void => {
    cy.get('.moonstone-menu_overlay').click({force: true});
    cy.get('.moonstone-menu_overlay').should('not.exist');
};

/**
 * Selects an item of a dropdown. The items are displayed above the page, outside of the dropdown.
 */
type ScanSummary = { id: string; startDate: string; workspace: string };

// fractionalSecondDigits is newer than the TypeScript library of the tests
const DATE_WITH_MILLISECONDS = {
    year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3
} as Intl.DateTimeFormatOptions;

const labelOf = (summary: ScanSummary, withMilliseconds: boolean): string => {
    const workspace = summary.workspace === 'all-workspaces' ? 'All workspaces' : summary.workspace;
    return `${new Date(summary.startDate).toLocaleString('en', withMilliseconds ? DATE_WITH_MILLISECONDS : undefined)} · ${workspace}`;
};

/**
 * The label of results in the Scan dropdown: the date of their scan and its workspace, with the milliseconds when another
 * scan of the same workspace started within the same second. The date is formatted by this browser, as the page does.
 */
export const getScanLabel = (resultsId: string): Cypress.Chainable<string> =>
    graphql('{ integrity: contentIntegrity { scanResults: scanResultsSummaries { id startDate workspace } } }')
        .then(data => {
            const summaries: ScanSummary[] = data.integrity.scanResults;
            const summary = summaries.find(s => s.id === resultsId);
            expect(summary, `Results ${resultsId}`).to.exist;
            const label = labelOf(summary as ScanSummary, false);
            const isShared = summaries.filter(s => labelOf(s, false) === label).length > 1;
            return isShared ? labelOf(summary as ScanSummary, true) : label;
        });

export const selectInDropdown = (label: string, item: string | RegExp): void => {
    openDropdown(label);
    getMenuItem(item).click();
};

/**
 * The labels of the header of the results table, without the column of the fix status, which is always the last one.
 */
export const getColumnLabels = (): Cypress.Chainable<string[]> =>
    getResultsTable().find('thead th').then(cells => cells.toArray().map(c => c.textContent.trim()).filter(l => l !== 'Fix status'));
