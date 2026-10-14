import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {
    clearFilters,
    closeDetailsPanel,
    getDetailsPanel,
    getDetailsWorkspace,
    getDialog,
    getNodeErrorRows,
    getNodeRow,
    getResultsTable,
    getRow,
    openErrorDetails,
    selectInDropdown,
    visitAdmin
} from '../../support/adminPage';

const SITE = 'ciUiFixErrors';
const SITE_PATH = `/sites/${SITE}`;
const LOCKS = `/sites/${SITE}/contents/locks`;
const MISSING_TEMPLATE = `/sites/${SITE}/home/missing-template`;
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

    it('opens the errors of the node on a click on the row of one of them, and offers no menu', () => {
        getResultsTable().find('button[aria-label="Actions"]').should('not.exist');
        openErrorDetails(MISSING_TEMPLATE);
        getRow(MISSING_TEMPLATE).should('have.class', 'moonstone-TableRow-selected');
        getDetailsPanel().within(() => {
            cy.contains('Missing template').should('be.visible');
            cy.contains('MISSING_TEMPLATE').should('be.visible');
            cy.contains('PagesSanityCheck').should('be.visible');
            cy.contains('ci-template-which-does-not-exist').should('be.visible');
        });
        // A click on a row of another node displays the errors of its node in the same panel
        openErrorDetails(`${LOCKS}/inconsistent-lock`);
        getDetailsPanel().should('contain.text', 'INCONSISTENT_LOCK').and('not.contain.text', 'MISSING_TEMPLATE');
        closeDetailsPanel();
    });

    it('closes the details with Escape, and gives the focus back to the row', () => {
        openErrorDetails(MISSING_TEMPLATE);
        cy.get('body').type('{esc}');
        cy.get('#ci-node-errors').should('not.exist');
        getRow(MISSING_TEMPLATE).should('have.focus');
    });

    it('opens the errors of a node on a click on the row of its path, above them', () => {
        getNodeRow(MISSING_TEMPLATE).should('contain.text', 'default').and('contain.text', MISSING_TEMPLATE);
        // The only error of the node is displayed in its row: its left end has no chevron
        getNodeRow(MISSING_TEMPLATE).click('left');
        getDetailsPanel().should('contain.text', MISSING_TEMPLATE);
        getDetailsWorkspace().should('have.text', 'default');
        getRow(MISSING_TEMPLATE).should('have.class', 'moonstone-TableRow-selected');
        cy.get('body').type('{esc}');
        getNodeRow(MISSING_TEMPLATE).should('have.focus');
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
        // A site without page: its check flags a page as home, and finds none
        runFixture('checks/HomePageDeclarationCheck.groovy', {SITEKEY: SITE, SCENARIO: 'NO_PAGE'});
        scan(`/sites/${SITE}`, [...CHECKS, 'HomePageDeclarationCheck']);
        cy.reload();
        clearFilters();
        selectInDropdown('Error type', /^NO_HOME \(1\)$/);
        cy.contains('button', 'Fix all').click();
        getDialog('Fix all the displayed errors').within(() => cy.contains('button', 'Fix 1 errors').click());
        // The fix has run without fixing the error: the dialog tells what the fix does, which says when it can't fix
        getDialog('Outcome of the fix', 30000).within(() => {
            cy.get('[data-fix-outcome=fixed]').should('have.text', 'Fixed: 0');
            cy.get('[data-fix-outcome=skipped]').should('have.text', 'Skipped: 0');
            cy.get('[data-fix-outcome=failed]').should('contain.text', 'Not fixed: 1').within(() => {
                // Told once for all the errors whose fix tells no reason
                cy.get('[data-no-message]').should('have.length', 1).and('have.text', 'The fix has run without fixing the error. The details are in jahia.log.');
                cy.get('[data-error-type=NO_HOME]')
                    .should('contain.text', 'HomePageDeclarationCheck · NO_HOME: 1')
                    .and('contain.text', 'Sets the first page of the site as its home page.')
                    .and('not.contain.text', 'The fix has run without fixing the error');
            });
            cy.contains('button', 'Close').click();
        });
        getRow(SITE_PATH).within(() => cy.contains('Not fixed').should('be.visible'));
        openErrorDetails(SITE_PATH);
        getDetailsPanel().within(() => {
            cy.contains('Not fixed').should('be.visible');
            cy.contains('The fix of the check has not fixed this error').should('be.visible');
        });
        // The failure is stored with the results: it is told again once the page is reloaded, and filtered
        cy.reload();
        clearFilters();
        selectInDropdown('Fix status', /^Not fixed \(1\)$/);
        getResultsTable().find('tr[data-error-id]').should('have.length', 1);
        getRow(SITE_PATH).should('contain.text', 'NO_HOME').within(() => cy.contains('Not fixed').should('be.visible'));
        runFixture('checks/HomePageDeclarationCheck.groovy', {SITEKEY: SITE, SCENARIO: 'RESTORE'});
    });

    it('fixes a page without template with a template chosen in a dropdown', () => {
        openErrorDetails(MISSING_TEMPLATE);
        getDetailsPanel().within(() => {
            cy.contains('The template ci-template-which-does-not-exist does not exist').should('be.visible');
            cy.contains('button', /^Fix$/).should('be.disabled');
        });
        // The menu of the dropdown is displayed above the page, outside of the panel
        getDetailsPanel().find('.moonstone-dropdown').click();
        cy.contains('li.moonstone-menuItem', /^home$/).click();
        getDetailsPanel().within(() => {
            cy.contains('button', /^Fix$/).click();
            cy.contains('Fixed').should('be.visible');
        });
        closeDetailsPanel();
        getRow(MISSING_TEMPLATE).within(() => cy.contains('Fixed').should('be.visible'));
        scan(`/sites/${SITE}`, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === MISSING_TEMPLATE)).to.have.length(0));
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('fixes all the errors of a node from the bottom of the panel', () => {
        const translation = `${LOCKS}/deletion-lock-on-translation/j:translation_en`;
        openErrorDetails(translation);
        getDetailsPanel().contains('button', /^Fix all \(2\)$/).click();
        getDetailsPanel().find('[data-error-id]').filter(':contains("Fixed")').should('have.length', 2);
        // Nothing is left to fix: Fix all stays at the bottom of the panel of a node with several errors, disabled
        getDetailsPanel().contains('button', /^Fix all \(0\)$/).should('be.disabled');
        scan(LOCKS, ['LockSanityCheck']).then(results => expect(results.errors.filter(e => e.nodePath === translation)).to.have.length(0));
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('fixes each error of a node with its own fix when they are not linked, and keeps their fixed status once the page is reloaded', () => {
        const translation = `${LOCKS}/deletion-lock-on-translation/j:translation_en`;
        getNodeErrorRows(translation).should('have.length', 2);
        openErrorDetails(translation);
        getDetailsPanel().within(() => {
            cy.contains('button', /^Fix \d+ errors$/).should('not.exist');
            cy.get('[data-error-id]').should('have.length', 2).each(card => cy.wrap(card).contains('button', /^Fix$/).click());
            cy.get('[data-error-id]').filter(':contains("Fixed")').should('have.length', 2);
        });
        closeDetailsPanel();
        getNodeErrorRows(translation).each(row => cy.wrap(row).within(() => cy.contains('Fixed').should('be.visible')));
        cy.reload();
        clearFilters();
        getNodeErrorRows(translation).should('have.length', 2).each(row => cy.wrap(row).within(() => cy.contains('Fixed').should('be.visible')));
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });
});
