import {graphql, readExecution, registerSlowCheck, startScan, unregisterSlowCheck, waitForExecution} from '../../support/integrity';
import {getCurrentScanCard, getDialog, visitAdmin} from '../../support/adminPage';

// About 15 seconds with CiSlowCheck: long enough to act while the scan runs
const SLOW_ROOT = '/sites/systemsite';

/**
 * Stops the scan and expects the page to remove its card, which is displayed only while a scan runs. A scan stopped
 * once its progress is logged has stored the errors found until then: the page displays it as interrupted, without
 * its errors.
 */
const stopFromThePage = (isScanInProgress = false): void => {
    cy.contains('button', 'Stop').click();
    cy.get('#ci-exec-title', {timeout: 30000}).should('not.exist');
    if (isScanInProgress) {
        cy.get('#ci-scan-status').should('have.text', 'Interrupted');
        cy.contains('The scan was interrupted before its end, so its errors are not displayed.').should('be.visible');
        cy.get('#ci-filters').should('not.exist');
        cy.get('[aria-label="Integrity errors"]').should('not.exist');
    }

    cy.contains('button', 'Stop').should('not.exist');
    cy.contains('button', 'New scan').should('not.be.disabled');
};

describe('Scan execution in the administration page', () => {
    before(() => registerSlowCheck());

    after(() => unregisterSlowCheck());

    // A test which fails while a scan runs must not leave it running for the next one
    afterEach(() => {
        readExecution().then(execution => {
            if (execution?.status === 'running') {
                graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { stopRunningScan } } }', {id: execution.id});
                waitForExecution(execution.id);
            }
        });
    });

    it('follows the scan it starts, with its logs, and stops it', () => {
        visitAdmin();
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').clear().type(SLOW_ROOT);
            cy.contains('button', 'Unselect all').click();
            cy.get('#ci-check-CiSlowCheck').click({force: true});
            cy.contains('button', 'Run the scan').click();
        });
        getCurrentScanCard().within(() => {
            cy.contains(/^Started on /).should('be.visible');
            cy.contains('It continues in the background if you leave this page').should('be.visible');
            // The logs are displayed while the scan runs, and follow its progress
            cy.get('[role=log]').should('contain.text', 'Scan progress');
        });
        // A single scan can run at a time
        cy.contains('button', 'New scan').should('be.disabled');
        stopFromThePage(true);
    });

    it('follows the scan which runs when the page is opened', () => {
        startScan({startNode: SLOW_ROOT, checks: ['CiSlowCheck']}).then(id => {
            visitAdmin();
            getCurrentScanCard().within(() => {
                cy.contains(/^Started on /).should('be.visible');
            });
            stopFromThePage();
            readExecution(id).then(execution => {
                expect(execution.status).to.equal('interrupted');
                expect(execution.logs.join('\n')).to.contain('Scan interrupted before the end');
            });
        });
    });
});
