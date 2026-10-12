import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {closeDetailsPanel, getDetailsPanel, getNodeErrorRows, getRow, openErrorDetails, selectInDropdown, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiFixWithValue';
const ROOT = `/sites/${SITE}/contents/property-definitions`;
// A jnt:frame without its mandatory width: the definition has no default value
const MISSING_MANDATORY = `${ROOT}/missing-mandatory`;
// ciTestChoice holds not-a-choice, and only accepts first and second
const INVALID_VALUES = `${ROOT}/invalid-values`;
// A jnt:frame without its width and its height: neither is set alone, since the node is saved only with both
const NODE_ERRORS = `/sites/${SITE}/contents/node-errors`;
const FRAME = `${NODE_ERRORS}/frame-without-size`;

describe('Fix of a missing mandatory property with a typed value', () => {
    beforeEach(() => {
        createTestSite(SITE);
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, ['PropertyDefinitionsSanityCheck']);
        visitAdmin();
    });

    after(() => {
        runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('displays the field of the value, and a Fix button disabled until a value is typed', () => {
        openErrorDetails(MISSING_MANDATORY);
        getDetailsPanel().within(() => {
            cy.contains('Fix with a value').should('be.visible');
            cy.contains('The mandatory property width has no value').should('be.visible');
            cy.contains('Type: Long').should('exist');
            // The Fix button of the panel is the only fix: it sends the typed value
            cy.contains('button', 'Set the value and fix').should('not.exist');
            cy.get('button').filter((i, button) => /^fix$/i.test(button.textContent.trim())).should('have.length', 1);
            cy.contains('button', /^Fix$/).should('be.disabled');
            cy.get('input[aria-label="width"]').should('be.visible').and('have.value', '').type('640');
            cy.contains('button', /^Fix$/).should('be.enabled');
            cy.get('input[aria-label="width"]').clear();
            cy.contains('button', /^Fix$/).should('be.disabled');
            // The field of the value comes first, the details below it
            cy.contains('EMPTY_MANDATORY_PROPERTY').scrollIntoView().should('be.visible');
        });
    });

    it('displays why a typed value is rejected, then fixes the error with a valid value', () => {
        openErrorDetails(MISSING_MANDATORY);
        getDetailsPanel().within(() => {
            cy.get('input[aria-label="width"]').type('not a number');
            cy.contains('button', /^Fix$/).click();
            cy.contains('not a number').should('be.visible');
            // A rejected value is not a failed fix: it is corrected in its field
            cy.contains('Not fixed').should('not.exist');
            cy.get('input[aria-label="width"]').clear().type('640{enter}');
            cy.contains('Fixed').should('be.visible');
            cy.contains('Fix with a value').should('not.exist');
            cy.contains('button', /^Fix$/).should('not.exist');
        });
        closeDetailsPanel();
        getRow(MISSING_MANDATORY).within(() => cy.contains('Fixed').should('be.visible'));
        scan(ROOT, ['PropertyDefinitionsSanityCheck']).then(results => {
            expect(results.errors.filter(e => e.errorType === 'EMPTY_MANDATORY_PROPERTY' && e.nodePath === MISSING_MANDATORY)).to.have.length(0);
        });
    });

    it('fixes a value which breaks the constraints with a value chosen among the accepted ones, apart from the other errors of its node', () => {
        selectInDropdown('Error type', /^INVALID_VALUE_CONSTRAINT/);
        openErrorDetails(INVALID_VALUES);
        // The panel displays all the errors of the node, whatever the filters. A single one takes a value: each has its own fix
        getDetailsPanel().within(() => {
            cy.contains('UNDECLARED_PROPERTY').should('exist');
            cy.contains('button', /^Fix \d+ errors$/).should('not.exist');
            // Fix all leaves an error fixed with values until its value is chosen
            cy.contains('button', /^Fix all \(1\)$/).should('be.enabled');
            cy.contains('[data-error-id]', 'The value not-a-choice of the property ciTestChoice does not match').within(() => {
                cy.contains('Type: String').should('not.exist');
                cy.contains('button', /^Fix$/).should('be.disabled');
            });
        });
        // The menu of the dropdown is displayed above the page, outside of the panel
        getDetailsPanel().find('.moonstone-dropdown').click();
        cy.get('li.moonstone-menuItem').should('have.length', 2);
        cy.contains('li.moonstone-menuItem', /^second$/).click();
        getDetailsPanel().within(() => {
            cy.contains('button', /^Fix all \(2\)$/).should('be.enabled');
            cy.contains('[data-error-id]', 'INVALID_VALUE_CONSTRAINT').within(() => {
                cy.contains('button', /^Fix$/).click();
                cy.contains('Fixed').should('be.visible');
            });
            // The undeclared property is left to its own fix
            cy.contains('[data-error-id]', 'UNDECLARED_PROPERTY').contains('button', /^Fix$/).should('be.enabled');
        });
        closeDetailsPanel();
        getRow(INVALID_VALUES).within(() => cy.contains('Fixed').should('be.visible'));
    });

    it('fixes the missing mandatory properties of a node together, once both have a value', () => {
        runFixture('nodeErrors/frameWithoutSize.groovy', {SITEKEY: SITE});
        scan(NODE_ERRORS, ['PropertyDefinitionsSanityCheck']);
        cy.reload();
        getNodeErrorRows(FRAME).should('have.length', 2);
        openErrorDetails(FRAME);
        getDetailsPanel().within(() => {
            // Linked, the two errors have a single fix
            // The panel scrolls to the error of the clicked row: the summary of the node is above it
            cy.contains('The values of these errors are saved at once').scrollIntoView().should('be.visible');
            cy.contains('button', /^Fix$/).should('not.exist');
            cy.contains('button', /^Fix 2 errors$/).should('be.disabled');
            cy.get('input[aria-label="width"]').type('640');
            // Saved alone, the width would be refused: the height is mandatory too
            cy.contains('button', /^Fix 2 errors$/).should('be.disabled');
            cy.get('input[aria-label="height"]').type('480');
            cy.contains('button', /^Fix 2 errors$/).click();
            cy.get('[data-error-id]').filter(':contains("Fixed")').should('have.length', 2);
        });
        closeDetailsPanel();
        getNodeErrorRows(FRAME).each(row => cy.wrap(row).within(() => cy.contains('Fixed').should('be.visible')));
        scan(NODE_ERRORS, ['PropertyDefinitionsSanityCheck']).then(results => expect(results.errors.filter(e => e.nodePath === FRAME)).to.have.length(0));
    });
});
