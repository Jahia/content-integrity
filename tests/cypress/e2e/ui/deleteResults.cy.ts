import {createTestSite, deleteTestSite, graphql, readStoredReport, runFixture, scan} from '../../support/integrity';
import {getDialog, getDropdown, getScanLabel, selectInDropdown, visitAdmin} from '../../support/adminPage';

// The button next to the reports deletes the selected results, with their reports, once confirmed
const SITE = 'ciUiDeleteResults';
const LOCKS = `/sites/${SITE}/contents/locks`;
const TITLE = 'Delete the results of the scan';

const selectNewResults = (): Cypress.Chainable<{ resultsId: string; label: string }> => scan(LOCKS, ['LockSanityCheck']).then(results => {
    const resultsId = results.resultsId as string;
    visitAdmin();
    return getScanLabel(resultsId).then(label => {
        selectInDropdown('Scan', label);
        return cy.wrap({resultsId, label}, {log: false});
    });
});

const isListed = (resultsId: string): Cypress.Chainable<boolean> =>
    graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id } } }')
        .then(data => data.integrity.summaries.some((s: { id: string }) => s.id === resultsId));

describe('Deletion of the results from the administration page', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    it('deletes the selected results, after a confirmation', () => {
        selectNewResults().then(({resultsId, label}) => {
            cy.get('#ci-delete-results').click();
            getDialog(TITLE).within(() => {
                cy.contains(label);
                cy.contains('The deletion can\'t be undone');
                cy.contains('button', 'Delete the results').click();
            });
            cy.contains(TITLE).should('not.exist');
            readStoredReport(resultsId).should('be.null');
            isListed(resultsId).should('be.false');
            // Other results are selected instead
            getDropdown('Scan').should('not.contain.text', label);
        });
    });

    it('keeps the results when the deletion is cancelled', () => {
        selectNewResults().then(({resultsId}) => {
            cy.get('#ci-delete-results').click();
            getDialog(TITLE).within(() => cy.contains('button', 'Cancel').click());
            cy.contains(TITLE).should('not.exist');
            readStoredReport(resultsId).should('not.be.null');
            graphql('query($id: String!) { integrity: contentIntegrity { deleteScanResults(id: $id) } }', {id: resultsId});
        });
    });
});
