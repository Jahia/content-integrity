import {graphql, readExecution, registerSlowCheck, startScan, unregisterSlowCheck, waitForExecution} from '../../support/integrity';

// About 15 seconds with CiSlowCheck: long enough to act while the scan runs
const SLOW_SCAN = {startNode: '/sites/systemsite', checks: ['CiSlowCheck']};

const stopExecution = (id: string): Cypress.Chainable<boolean> =>
    graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { stopRunningScan } } }', {id})
        .then(data => data.integrity.scan.stopRunningScan);

/**
 * Waits until the scan has scanned a few nodes, so that a stop interrupts it while it walks the tree.
 */
const waitForProgress = (id: string, attempt = 0): void => {
    readExecution(id).then(execution => {
        expect(execution.status, `Status of the scan ${id}`).to.equal('running');
        if (!execution.logs.join('\n').includes('Scan progress')) {
            expect(attempt, `The scan ${id} has not started to scan the tree`).to.be.lessThan(40);
            cy.wait(250, {log: false});
            waitForProgress(id, attempt + 1);
        }
    });
};

describe('Scan execution', () => {
    before(() => registerSlowCheck());

    after(() => unregisterSlowCheck());

    // A test which fails while a scan runs must not leave it running for the next one
    afterEach(() => {
        readExecution().then(execution => {
            if (execution?.status === 'running') {
                stopExecution(execution.id);
                waitForExecution(execution.id);
            }
        });
    });

    it('returns the scan which runs when no execution is given, with its start date', () => {
        const before = Date.now();
        startScan(SLOW_SCAN).then(id => {
            readExecution().then(execution => {
                expect(execution.id).to.equal(id);
                expect(execution.status).to.equal('running');
                // ISO-8601, in UTC; the browser and the server share the clock of this machine, within a second
                expect(execution.startDate).to.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/);
                expect(Date.parse(execution.startDate as string)).to.be.within(before - 1000, Date.now() + 1000);
            });
        });
    });

    it('refuses to start a scan while another one runs', () => {
        startScan(SLOW_SCAN).then(running => {
            waitForProgress(running);
            startScan({startNode: '/sites', checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(execution => {
                expect(execution.status).to.equal('failed');
                expect(execution.resultsID).to.be.null;
                expect(execution.logs).to.include('Impossible to run the integrity check, since another one is already running');
            });
            readExecution(running).its('status').should('equal', 'running');
        });
    });

    it('stops a running scan, which ends as interrupted', () => {
        startScan({...SLOW_SCAN, workspace: 'BOTH'}).then(id => {
            waitForProgress(id);
            stopExecution(id).should('equal', true);
            waitForExecution(id).then(execution => {
                expect(execution.status).to.equal('interrupted');
                expect(execution.logs).to.include('Scan interrupted before the end');
                // The live workspace is not scanned once the scan is stopped
                expect(execution.logs.join('\n')).to.not.contain('in the workspace live');
                // An interrupted scan has not checked all the content, so it does not claim that it found no error
                expect(execution.logs).to.not.include('No error found');
                // The errors found until the stop are stored, as the results of an interrupted scan
                expect(execution.resultsID).to.not.be.null;
                graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id status } } }').then(data => {
                    const summary = data.integrity.summaries.find((s: { id: string }) => s.id === execution.resultsID);
                    expect(summary.status).to.equal('interrupted');
                });
            });
            // An interrupted scan can not be stopped again
            stopExecution(id).should('equal', false);
        });
    });

    it('stores the results of a scan stopped before it checks any node, as interrupted', () => {
        // The whole repository, in both workspaces: the scan is still counting the nodes to scan when it is stopped
        startScan({startNode: '/', checks: ['LockSanityCheck'], workspace: 'BOTH'}).then(id => {
            stopExecution(id).should('equal', true);
            waitForExecution(id).then(execution => {
                expect(execution.status).to.equal('interrupted');
                expect(execution.logs.join('\n')).to.not.contain('Scan progress');
                expect(execution.logs).to.not.include('No error found');
                expect(execution.resultsID).to.not.be.null;
                graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id status errorCount } } }').then(data => {
                    const summary = data.integrity.summaries.find((s: { id: string }) => s.id === execution.resultsID);
                    expect(summary).to.deep.include({status: 'interrupted', errorCount: 0});
                });
            });
        });
    });

    it('does not stop the scan which runs from another execution', () => {
        startScan({startNode: '/sites/systemsite/home', checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(finished => {
            expect(finished.status).to.equal('finished');
            startScan(SLOW_SCAN).then(running => {
                waitForProgress(running);
                stopExecution(finished.id).should('equal', false);
                readExecution(finished.id).its('status').should('equal', 'finished');
                readExecution(running).its('status').should('equal', 'running');
            });
        });
    });
});
