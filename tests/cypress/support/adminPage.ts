/**
 * The administration page of the module, and its dialogs. The labels are the English ones.
 */
export const ADMIN_URL = '/jahia/administration/content-integrity';

export const visitAdmin = (): void => {
    cy.login();
    cy.visit(ADMIN_URL);
    cy.contains('h1, h2, h3', 'Content Integrity', {timeout: 60000}).should('be.visible');
};

export const getDialog = (title: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('[role=dialog]', title).should('be.visible');

export const getResultsTable = (): Cypress.Chainable<JQuery<HTMLElement>> => cy.get('[aria-label="Integrity errors"]', {timeout: 30000});

/**
 * The row of the results table which displays the node, among the rows displayed on the current page.
 */
export const getRow = (path: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    getResultsTable().find(`[title="${path}"]`).first().closest('tr');

/**
 * Opens the menu of the 3 dots of a row, which carries the actions on its error.
 */
export const openRowMenu = (path: string): void => {
    getRow(path).within(() => cy.get('button[aria-label="Actions"]').click());
};

/**
 * An item of the opened menu of a row. The menu is displayed above the page, outside of the row.
 */
export const getMenuItem = (label: string | RegExp): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('li.moonstone-menuItem', label).should('be.visible');
