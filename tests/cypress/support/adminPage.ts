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
export const selectInDropdown = (label: string, item: string | RegExp): void => {
    openDropdown(label);
    getMenuItem(item).click();
};

/**
 * The labels of the header of the results table, without the column of the actions.
 */
export const getColumnLabels = (): Cypress.Chainable<string[]> =>
    getResultsTable().find('thead th').then(cells => cells.toArray().map(c => c.innerText.trim()).filter(l => l !== 'Actions'));
