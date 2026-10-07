import {createTestSite, deleteTestSite, runFixture} from '../../support/integrity';
import {clearFilters, getDialog, getResultsTable, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiNewScan';
const ROOT = `/sites/${SITE}/contents/locks`;

describe('New scan', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    beforeEach(() => visitAdmin());

    it('lists the checks alphabetically, with the enabled ones selected', () => {
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').within(() => {
            cy.get('[id^="ci-check-"]').should('have.length.at.least', 26).then(boxes => {
                const ids = boxes.toArray().map(b => b.id.replace('ci-check-', ''));
                expect(ids).to.deep.equal([...ids].sort((a, b) => a.localeCompare(b, 'en', {sensitivity: 'base'})));
            });
            ['LivePropertiesCheck', 'NodeNameInfoSanityCheck', 'StaticInternalLinksCheck', 'VersionHistoryCheck', 'VersionSanityCheck'].forEach(id => {
                cy.get(`#ci-check-${id}`).should('not.be.checked');
            });
            ['AceSanityCheck', 'LockSanityCheck', 'PropertyDefinitionsSanityCheck'].forEach(id => {
                cy.get(`#ci-check-${id}`).should('be.checked');
            });
            cy.contains('button', 'Cancel').click();
        });
        cy.get('[role=dialog]').should('not.exist');
    });

    it('does not run a scan without any selected check', () => {
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').within(() => {
            cy.contains('button', 'Unselect all').click();
            cy.contains('Integrity checks (0 of').should('be.visible');
            cy.contains('button', 'Run the scan').should('be.disabled');
            cy.contains('button', 'Cancel').click();
        });
    });

    it('runs a scan of a subtree with the selected checks, then displays its results', () => {
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').clear().type(ROOT);
            cy.contains('button', 'Unselect all').click();
            cy.get('#ci-check-LockSanityCheck').click({force: true});
            cy.contains('Integrity checks (1 of').should('be.visible');
            cy.contains('button', 'Run the scan').click();
        });
        // The lock errors do not block an XML import, so the default filter hides them
        cy.contains('Errors: 0 (total: 3)', {timeout: 60000}).should('be.visible');
        clearFilters();
        cy.contains('Errors: 3').should('be.visible');
        getResultsTable().within(() => {
            cy.contains(`${ROOT}/inconsistent-lock`).should('exist');
            cy.contains(`${ROOT}/deletion-lock-on-translation/j:translation_en`).should('exist');
        });
    });
});
