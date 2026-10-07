import {graphql, readExecution, registerSlowCheck, startScan, unregisterSlowCheck, waitForExecution} from '../../support/integrity';
import {getDialog, visitAdmin} from '../../support/adminPage';

// About 15 seconds with CiSlowCheck: long enough to act while the scan runs
const SLOW_ROOT = '/sites/systemsite';

const getExecutionCard = (title: string): Cypress.Chainable<JQuery<HTMLElement>> =>
    cy.contains('#ci-exec-title', title, {timeout: 30000}).closest('section');

/**
 * Stops the scan and expects the page to display it as stopped, with the end of its logs.
 */
const stopFromThePage = (): void => {
    cy.contains('button', 'Stop').click();
    getExecutionCard('Last scan').within(() => {
        cy.contains('Stopped', {timeout: 30000}).should('be.visible');
        cy.contains('button', 'Show the logs').click();
        cy.get('[role=log]').should('contain.text', 'Scan interrupted before the end');
    });
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
        getExecutionCard('Scan in progress').within(() => {
            cy.contains('Running').should('be.visible');
            cy.contains('It continues in the background if you leave this page').should('be.visible');
            // The logs are displayed while the scan runs, and follow its progress
            cy.get('[role=log]').should('contain.text', 'Scan progress');
        });
        // A single scan can run at a time
        cy.contains('button', 'New scan').should('be.disabled');
        stopFromThePage();
    });

    it('follows the scan which runs when the page is opened', () => {
        startScan({startNode: SLOW_ROOT, checks: ['CiSlowCheck']}).then(id => {
            visitAdmin();
            getExecutionCard('Scan in progress').within(() => {
                cy.contains('Running').should('be.visible');
                cy.contains(id).should('be.visible');
            });
            stopFromThePage();
            readExecution(id).its('status').should('equal', 'interrupted');
        });
    });
});
