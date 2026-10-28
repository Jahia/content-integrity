import {createTestSite, deleteTestSite, graphql, readStoredReport, runFixture, scan} from '../../support/integrity';
import {closeMenu, getDialog, getDropdown, getScanLabel, openDeleteAll, openDropdown, selectInDropdown, visitAdmin} from '../../support/adminPage';

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
        // Other results stay stored, so that the page keeps the dropdown of the scans: once no results are stored, it
        // displays that no scan has run yet, which the server does not ensure, as the deletion of all the results empties it
        scan(LOCKS, ['LockSanityCheck']).its('resultsId').as('otherResults');
        selectNewResults().then(({resultsId, label}) => {
            // A server administrator holds adminContentIntegrityDelete: the menu after the title offers to delete all
            cy.get('#ci-results-menu').should('be.visible');
            cy.get('#ci-delete-results').click();
            getDialog(TITLE).within(() => {
                cy.contains(label);
                cy.contains('The deletion can\'t be undone');
                cy.contains('button', 'Delete the results').click();
            });
            cy.contains(TITLE).should('not.exist');
            readStoredReport(resultsId).should('be.null');
            isListed(resultsId).should('be.false');
            // As after a refresh, no results are displayed until a scan is chosen
            getDropdown('Scan').should('contain.text', 'Select a scan');
            cy.get('#ci-scan-status').should('not.exist');
            cy.get('#ci-filters').should('not.exist');
            cy.get('#ci-delete-results').should('not.exist');
            // The same content as when no scan is stored, with the button to run a new scan
            cy.get('#ci-empty-state').within(() => {
                cy.contains('No scan selected');
                cy.contains('button', 'New scan').should('be.enabled');
            });
            openDropdown('Scan');
            cy.get('li.moonstone-menuItem').should('not.contain.text', label);
            closeMenu();
        });
        cy.get<string>('@otherResults').then(id => graphql('query($id: String!) { integrity: contentIntegrity { deleteScanResults(id: $id) } }', {id}));
    });

    it('opens without any results selected, also once the page is reloaded', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            visitAdmin();
            const expectNothingSelected = () => {
                getDropdown('Scan').should('contain.text', 'Select a scan');
                cy.get('#ci-scan-status').should('not.exist');
                cy.get('#ci-empty-state').should('contain.text', 'No scan selected');
            };
            expectNothingSelected();
            cy.reload();
            expectNothingSelected();
            graphql('query($id: String!) { integrity: contentIntegrity { deleteScanResults(id: $id) } }', {id: results.resultsId});
        });
    });

    it('does not delete all the results when the deletion is cancelled', () => {
        selectNewResults().then(({resultsId}) => {
            openDeleteAll();
            getDialog('Delete the results of all the scans').within(() => {
                cy.contains('The results of a running scan are kept.');
                cy.contains('button', 'Cancel').click();
            });
            cy.contains('Delete the results of all the scans').should('not.exist');
            readStoredReport(resultsId).should('not.be.null');
            graphql('query($id: String!) { integrity: contentIntegrity { deleteScanResults(id: $id) } }', {id: resultsId});
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
