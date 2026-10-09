import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {closeDetailsPanel, getDetailsPanel, getRow, openErrorDetails, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiFixWithValue';
const ROOT = `/sites/${SITE}/contents/property-definitions`;
// A jnt:frame without its mandatory width: the definition has no default value
const MISSING_MANDATORY = `${ROOT}/missing-mandatory`;

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
});
