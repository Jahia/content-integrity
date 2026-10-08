import {followScan, readExecution, registerSlowCheck, SLOW_SCAN, startScan, stopRunningScan, unregisterSlowCheck, waitForExecution} from '../../support/integrity';
import {visitAdmin} from '../../support/adminPage';

describe('Scan subscription', () => {
    before(() => registerSlowCheck());

    after(() => unregisterSlowCheck());

    afterEach(() => stopRunningScan());

    it('pushes the logs of a scan until its end, each line once', () => {
        visitAdmin();
        startScan(SLOW_SCAN).then(id => {
            followScan(id).then(outcome => {
                expect(outcome.errors).to.be.empty;
                expect(outcome.completed).to.equal(true);
                const statuses = outcome.events.map(e => e.status);
                expect(statuses.length, 'events').to.be.greaterThan(2);
                expect(statuses.slice(0, -1).every(status => status === 'running')).to.equal(true);
                const last = outcome.events[outcome.events.length - 1];
                expect(last.status).to.equal('finished');
                expect(last.resultsID).to.not.be.null;
                expect(outcome.events[0].startDate).to.match(/^\d{4}-\d{2}-\d{2}T/);
                // The first event carries the lines written so far, the next ones only the new lines
                const streamed = outcome.events.reduce((lines: string[], e) => lines.concat(e.logs), []);
                readExecution(id).its('logs').should('deep.equal', streamed);
            });
        });
    });

    it('sends the final state of a scan which is over, then ends', () => {
        startScan({startNode: '/sites/systemsite/home', checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(execution => {
            visitAdmin();
            followScan(execution.id).then(outcome => {
                expect(outcome.completed).to.equal(true);
                expect(outcome.events).to.have.length(1);
                expect(outcome.events[0]).to.deep.include({status: 'finished', resultsID: execution.resultsID, logs: execution.logs});
            });
        });
    });

    it('refuses a guest', () => {
        startScan({startNode: '/sites/systemsite/home', checks: ['LockSanityCheck']}).then(id => {
            visitAdmin();
            cy.clearCookies();
            followScan(id).then(outcome => {
                expect(outcome.events).to.be.empty;
                expect(outcome.completed).to.equal(false);
                expect(outcome.errors).to.not.be.empty;
            });
        });
    });
});
