import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {getDialog, getMenuItem, getRow, openRowMenu, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiFixWithValue';
const ROOT = `/sites/${SITE}/contents/property-definitions`;
const MISSING_MANDATORY = `${ROOT}/missing-mandatory`;

describe('Fix of a missing mandatory property with a typed value', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, ['PropertyDefinitionsSanityCheck']);
    });

    after(() => {
        runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    beforeEach(() => visitAdmin());

    it('opens the details of the error from the fix of its menu', () => {
        openRowMenu(MISSING_MANDATORY);
        getMenuItem('Fix…').click();
        getDialog('Error details').within(() => {
            cy.contains('Fix with a value').should('be.visible');
            cy.contains('The mandatory property width has no value').should('be.visible');
            cy.contains('Type: Long').should('exist');
            cy.contains('button', 'Set the value and fix').should('be.disabled');
            cy.contains('button', 'Close').click();
        });
    });

    it('displays why a value is rejected, and fixes the error with a valid value', () => {
        openRowMenu(MISSING_MANDATORY);
        getMenuItem('Fix…').click();
        getDialog('Error details').within(() => {
            cy.get('input[aria-label="width"]').type('not a number');
            cy.contains('button', 'Set the value and fix').click();
            cy.contains('not a number').should('be.visible');
            cy.get('input[aria-label="width"]').clear().type('640');
            cy.contains('button', 'Set the value and fix').click();
            cy.contains('Fixed').should('be.visible');
            cy.contains('Fix with a value').should('not.exist');
            cy.contains('button', 'Close').click();
        });
        getRow(MISSING_MANDATORY).within(() => cy.contains('Fixed').should('be.visible'));
        scan(ROOT, ['PropertyDefinitionsSanityCheck']).then(results => {
            expect(results.errors.filter(e => e.errorType === 'EMPTY_MANDATORY_PROPERTY' && e.nodePath === MISSING_MANDATORY)).to.have.length(0);
        });
    });
});
