import {createTestSite, deleteTestSite, graphql, runFixture, scan} from '../../support/integrity';
import {closeDetailsPanel, confirmFix, getDetailsPanel, getDialog, getNodeErrorRows, getRow, openErrorDetails, selectInDropdown, visitLatestResults} from '../../support/adminPage';

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
        visitLatestResults();
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
            confirmFix(1);
            cy.contains('not a number').should('be.visible');
            // A rejected value is not a failed fix: it is corrected in its field
            cy.contains('Not fixed').should('not.exist');
            cy.get('input[aria-label="width"]').clear().type('640{enter}');
            confirmFix(1);
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
                confirmFix(1);
                cy.contains('Fixed').should('be.visible');
            });
            // The undeclared property is left to its own fix
            cy.contains('[data-error-id]', 'UNDECLARED_PROPERTY').contains('button', /^Fix$/).should('be.enabled');
        });
        closeDetailsPanel();
        getRow(INVALID_VALUES).within(() => cy.contains('Fixed').should('be.visible'));
    });

    it('tells after the fix of all the errors of a node how many are fixed, and why the other ones are skipped', () => {
        openErrorDetails(INVALID_VALUES);
        // The undeclared property has a fix, the values of the wrong type have none, the value which breaks the constraints waits for a value
        getDetailsPanel().contains('button', /^Fix all \(1\)$/).click();
        confirmFix(1);
        getDialog('Outcome of the fix', 30000).within(() => {
            cy.get('[data-fix-outcome=fixed]').should('have.text', 'Fixed: 1');
            cy.get('[data-fix-outcome=skipped]').should('contain.text', 'Skipped: 3').within(() => {
                cy.get('[data-reason=NO_FIX]').should('have.text', '2 without fix: their check provides none');
                cy.get('[data-reason=VALUES_NOT_TYPED]').should('have.text', '1 waiting for a value: type it above their Fix button');
            });
            cy.get('[data-fix-outcome=failed]').should('have.text', 'Not fixed: 0');
            cy.contains('button', 'Close').click();
        });
        // The panel stays open, the error fixed is tagged
        getDetailsPanel().contains('[data-error-id]', 'UNDECLARED_PROPERTY').contains('Fixed').scrollIntoView().should('be.visible');
    });

    it('tells that the node of an error has been removed since the scan, instead of a missing fix', () => {
        graphql('mutation($path: String!) { jcr { deleteNode(pathOrId: $path) } }', {path: INVALID_VALUES});
        openErrorDetails(INVALID_VALUES);
        getDetailsPanel().within(() => {
            // The undeclared property has a fix, but no node left to fix: none of its errors is flagged without fix
            cy.get('[data-error-id]').should('have.length', 4).each(error => cy.wrap(error).within(() => {
                cy.contains('Node removed').should('exist');
                cy.contains('No fix').should('not.exist');
                cy.get('[data-node-removed]').should('contain.text', 'The node no longer exists');
                cy.contains('button', /^Fix$/).should('not.exist');
            }));
        });
        getDetailsPanel().contains('button', /^Fix all \(0\)$/).should('be.disabled');
    });

    it('fixes the missing mandatory properties of a node together, once both have a value', () => {
        runFixture('nodeErrors/frameWithoutSize.groovy', {SITEKEY: SITE});
        scan(NODE_ERRORS, ['PropertyDefinitionsSanityCheck']);
        visitLatestResults();
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
            confirmFix(2);
            cy.get('[data-error-id]').filter(':contains("Fixed")').should('have.length', 2);
        });
        closeDetailsPanel();
        getNodeErrorRows(FRAME).each(row => cy.wrap(row).within(() => cy.contains('Fixed').should('be.visible')));
        scan(NODE_ERRORS, ['PropertyDefinitionsSanityCheck']).then(results => expect(results.errors.filter(e => e.nodePath === FRAME)).to.have.length(0));
    });
});
