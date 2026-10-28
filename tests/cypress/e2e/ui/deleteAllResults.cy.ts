import {
    createTestSite,
    deleteTestSite,
    graphql,
    readExecution,
    readStoredReport,
    registerSlowCheck,
    runFixture,
    scan,
    SLOW_SCAN,
    startScan,
    stopRunningScan,
    unregisterSlowCheck
} from '../../support/integrity';
import {getDialog, openDeleteAll, visitAdmin} from '../../support/adminPage';

// The deletion of the results of all the scans. WARNING: this spec deletes EVERY stored report of the server, so it must
// only run on a server whose reports can be lost, such as the one of the CI.
const SITE = 'ciUiDeleteAllResults';
const LOCKS = `/sites/${SITE}/contents/locks`;
const TITLE = 'Delete the results of all the scans';

const listResults = (): Cypress.Chainable<string[]> =>
    graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id } } }')
        .then(data => data.integrity.summaries.map((s: { id: string }) => s.id));

describe('Deletion of the results of all the scans', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    it('deletes the results of all the scans, except the ones of a running scan', () => {
        scan(LOCKS, ['LockSanityCheck']).then(first => scan(LOCKS, ['LockSanityCheck']).then(second => {
            registerSlowCheck();
            startScan(SLOW_SCAN).then(id => readExecution(id)).then(running => {
                listResults().then(before => {
                    graphql('{ integrity: contentIntegrity { deleted: deleteAllScanResults } }')
                        .its('integrity.deleted').should('equal', before.length - 1);
                });
                readStoredReport(first.resultsId as string).should('be.null');
                readStoredReport(second.resultsId as string).should('be.null');
                readStoredReport(running.resultsID as string).should('not.be.null');
                listResults().should('deep.equal', [running.resultsID]);
            });
            stopRunningScan();
            unregisterSlowCheck();
        }));
    });

    it('deletes the results of all the scans from the administration page, after a confirmation', () => {
        scan(LOCKS, ['LockSanityCheck']).then(() => scan(LOCKS, ['LockSanityCheck'])).then(() => {
            listResults().then(stored => {
                visitAdmin();
                openDeleteAll();
                getDialog(TITLE).within(() => {
                    cy.contains(`The results of the ${stored.length} scans will be deleted`);
                    // The deletion is confirmed only once the administrator has ticked that the data are going to be altered
                    cy.contains('button', `Delete ${stored.length} results`).should('be.disabled');
                    cy.get('#ci-acknowledge').should('not.be.checked').click({force: true});
                    cy.contains('button', `Delete ${stored.length} results`).should('be.enabled').click();
                });
                cy.contains(TITLE).should('not.exist');
                listResults().should('have.length', 0);
                // No scan is stored anymore: the page invites to run one
                cy.get('#ci-empty-state').should('contain.text', 'No scan results yet');
                cy.get('#ci-results-menu').should('not.exist');
            });
        });
    });
});
