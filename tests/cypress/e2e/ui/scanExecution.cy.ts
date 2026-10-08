import {readExecution, registerSlowCheck, REPORTS_PATH, SLOW_SCAN, startScan, stopRunningScan, unregisterSlowCheck, waitForExecution} from '../../support/integrity';
import {ADMIN_URL, getCurrentScanCard, getDialog, getDropdown, getScanLabel, selectInDropdown, visitAdmin} from '../../support/adminPage';

// The query with which the page polls a scan
const POLL_OPERATION = 'ContentIntegrityScan';

// Apollo batches the queries, so a request carries one or several operations
const operationsOf = (body: unknown): string[] => (Array.isArray(body) ? body : [body]).map(o => o?.operationName);

/**
 * Records the GraphQL requests of the page, as @graphql. The alias is static, so that it exists even when no request
 * polls the scan.
 */
const interceptGraphql = (): void => {
    cy.intercept('POST', '**/modules/graphql').as('graphql');
};

const countScanPolls = (): Cypress.Chainable<number> =>
    cy.get<{ request: { body: unknown } }[]>('@graphql.all').then(calls => calls.filter(c => operationsOf(c.request.body).includes(POLL_OPERATION)).length);

/**
 * A WebSocket whose connection fails at once, as behind a proxy which does not let WebSockets through.
 */
class FailingWebSocket {
    static OPEN = 1;
    readyState = 3;
    onclose: ((event: { code: number }) => void) | null = null;
    onerror: (() => void) | null = null;

    constructor() {
        setTimeout(() => {
            this.onerror?.();
            this.onclose?.({code: 1006});
        });
    }

    send(): void {
        // Never connected: nothing to send
    }

    close(): void {
        // Already closed
    }
}

/**
 * Stops the scan and expects the page to remove its card, which is displayed only while a scan runs. A scan stopped
 * once its progress is logged has stored the errors found until then: the page displays it as interrupted, without
 * its errors.
 */
const stopFromThePage = (isScanInProgress = false): void => {
    cy.contains('button', /^Stop$/).click();
    // The scan stops at the end of its current step: until then, the page tells it is stopping, and Stop is disabled
    cy.contains('button', 'Stopping…').should('be.disabled');
    cy.get('#ci-exec-title').should('have.text', 'Stopping the scan');
    cy.get('#ci-exec-title', {timeout: 30000}).should('not.exist');
    if (isScanInProgress) {
        cy.get('#ci-scan-status').should('have.text', 'Interrupted');
        cy.contains('The scan was interrupted before its end, so its errors are not displayed.').should('be.visible');
        cy.get('#ci-filters').should('not.exist');
        cy.get('[aria-label="Integrity errors"]').should('not.exist');
    }

    cy.contains('button', /^Stop/).should('not.exist');
    cy.contains('button', 'New scan').should('not.be.disabled');
};

describe('Scan execution in the administration page', () => {
    // One second per node: the end of the step a stop waits for is long enough to see the page stopping
    before(() => registerSlowCheck(1000));

    after(() => unregisterSlowCheck());

    afterEach(() => stopRunningScan());

    it('follows the scan it starts, with its logs, and stops it', () => {
        visitAdmin();
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').clear().type(SLOW_SCAN.startNode);
            cy.get('#ci-excluded-paths').type(`${REPORTS_PATH}{enter}`);
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

    it('receives the logs of the scan through the subscription, without polling it', () => {
        interceptGraphql();
        startScan(SLOW_SCAN).then(() => {
            visitAdmin();
            // A polling page reads the scan as soon as it opens, long before its progress is logged
            getCurrentScanCard().within(() => cy.get('[role=log]').should('contain.text', 'Scan progress'));
            countScanPolls().should('equal', 0);
            stopFromThePage();
        });
    });

    it('polls the scan when the WebSocket connection fails', () => {
        interceptGraphql();
        startScan(SLOW_SCAN).then(() => {
            cy.login();
            cy.visit(ADMIN_URL, {
                onBeforeLoad: win => {
                    (win as unknown as { WebSocket: unknown }).WebSocket = FailingWebSocket;
                }
            });
            // The logs come from the polling
            getCurrentScanCard().within(() => cy.get('[role=log]').should('contain.text', 'Scan progress'));
            countScanPolls().should('be.greaterThan', 0);
            stopFromThePage();
        });
    });

    it('lists a running scan, but selects the latest results of a scan which is over', () => {
        startScan({startNode: '/sites/systemsite/home', checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(over => {
            startScan(SLOW_SCAN).then(id => readExecution(id)).then(running => {
                visitAdmin();
                getCurrentScanCard().should('exist');
                // The card of the scan follows it: the results card displays the results of the last scan which is over
                getScanLabel(over.resultsID as string).then(label => getDropdown('Scan').should('contain.text', label));
                getScanLabel(running.resultsID as string).then(label => selectInDropdown('Scan', label));
                cy.get('#ci-scan-status').should('have.text', 'Running');
                cy.contains('The scan is running: its errors are displayed once it ends.').should('be.visible');
                stopFromThePage();
            });
        });
    });

    it('follows the scan which runs when the page is opened', () => {
        startScan(SLOW_SCAN).then(id => {
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
