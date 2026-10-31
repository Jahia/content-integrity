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
    unregisterSlowCheck,
    waitForExecution
} from '../../support/integrity';

const SITE = 'ciApiScanReports';
const LOCKS = `/sites/${SITE}/contents/locks`;

// null when the results are not listed: a callback of then() which returns undefined yields its subject again
const readSummary = (resultsId: string): Cypress.Chainable<{ status: string; errorCount: number } | null> =>
    graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id status errorCount } } }')
        .then(data => data.integrity.summaries.find((s: { id: string }) => s.id === resultsId) ?? null);

/**
 * Waits until the stored report is deleted.
 */
const waitForDeletion = (resultsId: string, attempt = 0): void => {
    readStoredReport(resultsId).then(report => {
        if (report !== null) {
            expect(attempt, `The report ${resultsId} is still stored`).to.be.lessThan(30);
            cy.wait(1000, {log: false});
            waitForDeletion(resultsId, attempt + 1);
        }
    });
};

describe('Scan reports stored in the JCR', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        registerSlowCheck();
    });

    afterEach(() => stopRunningScan());

    after(() => {
        unregisterSlowCheck();
        runFixture('reports/configureCleanup.groovy', {RETENTION_DAYS: '30', CLEANUP_INTERVAL_HOURS: '24'});
        deleteTestSite(SITE);
    });

    it('stores the report of a scan from its start, then its errors once it is over', () => {
        startScan(SLOW_SCAN).then(id => {
            readExecution(id).then(execution => {
                // The results are identified, and their report stored, as soon as the scan starts
                expect(execution.status).to.equal('running');
                expect(execution.resultsID).to.not.be.null;
                const resultsId = execution.resultsID as string;
                readStoredReport(resultsId).then(report => {
                    expect(report?.status).to.equal('running');
                    expect(report?.files.filter(f => f.endsWith('-errors.json.gz'))).to.be.empty;
                });
                readSummary(resultsId).its('status').should('equal', 'running');

                waitForExecution(id).its('status').should('equal', 'finished');
                readStoredReport(resultsId).then(report => {
                    expect(report?.status).to.equal('finished');
                    expect(report?.files).to.include(`${resultsId}-full-errors.json.gz`);
                });
                readSummary(resultsId).its('status').should('equal', 'finished');
            });
        });
    });

    it('stores a scan which failed, with the log which tells why', () => {
        startScan({startNode: `/sites/${SITE}/does-not-exist`, checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(execution => {
            expect(execution.status).to.equal('failed');
            expect(execution.logs.join('\n')).to.contain('does not exist');
            readStoredReport(execution.resultsID as string).its('status').should('equal', 'failed');
            readSummary(execution.resultsID as string).its('status').should('equal', 'failed');
            // The log is stored with the report, and read without its errors
            graphql('query($id: String!) { integrity: contentIntegrity { logs: scanResultsLogs(id: $id) } }', {id: execution.resultsID})
                .its('integrity.logs').then(logs => expect((logs as string[]).join('\n')).to.contain('does not exist'));
        });
    });

    it('ignores a report whose errors are not stored', () => {
        const resultsId = 'default_2020_01_15-10_00_00_000';
        runFixture('reports/reportWithoutErrors.groovy', {RESULTS_ID: resultsId});
        readStoredReport(resultsId).its('status').should('equal', 'finished');
        readSummary(resultsId).should('be.null');
        graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { totalErrorCount } } }', {id: resultsId})
            .then(data => expect(data.integrity.results).to.equal(null));
    });

    it('returns no log for unknown results', () => {
        graphql('{ integrity: contentIntegrity { logs: scanResultsLogs(id: "default_2020_01_01-00_00_00_000") } }')
            .then(data => expect(data.integrity.logs).to.equal(null));
    });

    it('stores the errors fixed afterwards in the report, without rewriting its errors', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            const resultsId = results.resultsId as string;
            const error = results.errors.find(e => e.fixable && !e.fixed);
            expect(error, 'A fixable error').to.exist;
            graphql('query($id: String, $e: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: fixError(id: $e) { fixed } } } }',
                {id: resultsId, e: error.id}).its('integrity.results.error.fixed').should('equal', true);
            readStoredReport(resultsId).its('fixedErrors').should('include', error.id);
            graphql('query($id: String, $e: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: errorById(id: $e) { fixed } } } }',
                {id: resultsId, e: error.id}).its('integrity.results.error.fixed').should('equal', true);
            runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        });
    });

    it('deletes the reports older than the retention, in the background', () => {
        scan(LOCKS, ['LockSanityCheck']).then(old => scan(LOCKS, ['LockSanityCheck']).then(recent => {
            readStoredReport(old.resultsId as string).then(report => {
                runFixture('reports/backdateReport.groovy', {REPORT_PATH: (report as { path: string }).path, DATE: '2020-01-15T10:00:00.000Z'});
            });
            // A change of the configuration runs a cleanup at once
            runFixture('reports/configureCleanup.groovy', {RETENTION_DAYS: '30', CLEANUP_INTERVAL_HOURS: '23'});
            waitForDeletion(old.resultsId as string);
            readStoredReport(recent.resultsId as string).should('not.be.null');
        }));
    });
});
