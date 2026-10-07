import {createTestSite, deleteTestSite, getErrors, graphql, runFixture, scan} from '../../support/integrity';

const SITE = 'ciApiScan';
const LOCKS = `/sites/${SITE}/contents/locks`;

const startScan = (variables: Record<string, unknown>): Cypress.Chainable<string> =>
    graphql('query($workspace: WorkspaceToScan!, $startNode: String, $checks: [String], $upload: Boolean) { integrity: contentIntegrity { scan: integrityScan { id: scan(workspace: $workspace, startNode: $startNode, checksToRun: $checks, uploadResults: $upload) } } }',
        variables).then(data => data.integrity.scan.id);

const readScan = (id: string): Cypress.Chainable<any> =>
    graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { status resultsID logs reports { name extension uri } } } }', {id})
        .then(data => data.integrity.scan);

const waitForEnd = (id: string): Cypress.Chainable<any> => readScan(id).then(execution => {
    if (execution.status === 'running') {
        cy.wait(500, {log: false});
        return waitForEnd(id);
    }

    return cy.wrap(execution, {log: false});
});

describe('Scan API', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    it('does nothing when no check is selected', () => {
        startScan({workspace: 'EDIT', startNode: LOCKS, checks: []}).then(id => waitForEnd(id)).then(execution => {
            expect(execution.status).to.equal('finished');
            expect(execution.resultsID).to.be.null;
            expect(execution.logs).to.include('No check selected');
        });
    });

    it('reports a scan without error', () => {
        startScan({workspace: 'EDIT', startNode: `${LOCKS}/not-locked`, checks: ['LockSanityCheck']}).then(id => waitForEnd(id)).then(execution => {
            expect(execution.status).to.equal('finished');
            expect(execution.resultsID).to.be.null;
            expect(execution.logs.join('\n')).to.contain('No error found');
        });
    });

    it('stores the results of a scan with errors, and lists them', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            expect(results.resultsId).to.match(/^default_/);
            graphql('{ integrity: contentIntegrity { scanResults } }').then(data => expect(data.integrity.scanResults).to.include(results.resultsId));
            graphql('query($id: String, $error: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { errorCount totalErrorCount error: errorById(id: $error) { id errorType } } } }',
                {id: results.resultsId, error: results.errors[0].id}).then(data => {
                expect(data.integrity.results.errorCount).to.equal(results.errors.length);
                expect(data.integrity.results.totalErrorCount).to.equal(results.errors.length);
                expect(data.integrity.results.error.errorType).to.equal(results.errors[0].errorType);
            });
        });
    });

    it('filters the errors of scan results', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            graphql('query($id: String, $filters: [String]) { integrity: contentIntegrity { results: scanResultsDetails(id: $id, filters: $filters) { errorCount totalErrorCount } } }',
                {id: results.resultsId, filters: ['errorType;DELETION_LOCK_ON_I18N']}).then(data => {
                expect(data.integrity.results.errorCount).to.equal(1);
                expect(data.integrity.results.totalErrorCount).to.equal(results.errors.length);
            });
        });
    });

    it('scans both workspaces', () => {
        scan(LOCKS, ['LockSanityCheck'], 'BOTH').then(results => {
            expect(results.resultsId).to.not.be.null;
            getErrors(results.resultsId).then(errors => expect(errors.map(e => e.workspace)).to.include('default'));
        });
    });

    it('writes the reports of a scan in the JCR when requested', () => {
        startScan({workspace: 'EDIT', startNode: LOCKS, checks: ['LockSanityCheck'], upload: true}).then(id => waitForEnd(id)).then(execution => {
            expect(execution.status).to.equal('finished');
            expect(execution.reports.map((r: { extension: string }) => r.extension)).to.include.members(['csv']);
        });
    });

    it('answers for an unknown execution or unknown results', () => {
        readScan('unknown-execution').then(execution => expect(execution.status).to.equal('Unknown execution ID'));
        graphql('{ integrity: contentIntegrity { results: scanResultsDetails(id: "unknown-results") { errorCount } } }')
            .then(data => expect(data.integrity.results).to.be.null);
    });

    it('returns null when fixing an error which does not exist', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: fixError(id: "unknown-error") { id } } } }', {id: results.resultsId})
                .then(data => expect(data.integrity.results.error).to.be.null);
        });
    });
});
