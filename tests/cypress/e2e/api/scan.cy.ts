import {createTestSite, deleteTestSite, getErrors, graphql, readExecution, runFixture, scan, startScan, waitForExecution} from '../../support/integrity';

const SITE = 'ciApiScan';
const LOCKS = `/sites/${SITE}/contents/locks`;

describe('Scan API', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    it('does nothing when no check is selected', () => {
        startScan({startNode: LOCKS, checks: []}).then(id => waitForExecution(id)).then(execution => {
            expect(execution.status).to.equal('finished');
            expect(execution.resultsID).to.be.null;
            expect(execution.logs).to.include('No check selected');
        });
    });

    it('reports a scan without error', () => {
        startScan({startNode: `${LOCKS}/not-locked`, checks: ['LockSanityCheck']}).then(id => waitForExecution(id)).then(execution => {
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

    it('pages the errors of scan results', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            expect(results.errors).to.have.length.at.least(3);
            const page = (offset: number, pageSize: number): Cypress.Chainable<string[]> =>
                graphql('query($id: String, $offset: Int!, $size: Int!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { errors(offset: $offset, pageSize: $size) { id } } } }',
                    {id: results.resultsId, offset, size: pageSize}).then(data => data.integrity.results.errors.map((e: { id: string }) => e.id));
            page(0, 2).then(ids => expect(ids).to.deep.equal(results.errors.slice(0, 2).map(e => e.id)));
            page(2, 2).then(ids => expect(ids).to.deep.equal(results.errors.slice(2, 4).map(e => e.id)));
        });
    });

    it('counts the values of the columns, each one ignoring its own filter', () => {
        scan(LOCKS, ['LockSanityCheck']).then(results => {
            const countsByType: Record<string, number> = {};
            results.errors.forEach(e => {
                countsByType[e.errorType] = (countsByType[e.errorType] || 0) + 1;
            });
            graphql('query($id: String, $filters: [String]) { integrity: contentIntegrity { results: scanResultsDetails(id: $id, filters: $filters) { possibleValues(names: ["errorType", "checkName"], withErrorsOnly: true) { name values { name count } } } } }',
                {id: results.resultsId, filters: ['errorType;DELETION_LOCK_ON_I18N']}).then(data => {
                const columns: Record<string, Record<string, number>> = {};
                data.integrity.results.possibleValues.forEach((c: { name: string; values: { name: string; count: number }[] }) => {
                    columns[c.name] = Object.fromEntries(c.values.map(v => [v.name, v.count]));
                });
                // The filter on the error type does not restrict the values offered for the error type
                expect(columns.errorType).to.deep.equal(countsByType);
                expect(columns.checkName).to.deep.equal({LockSanityCheck: 1});
            });
        });
    });

    it('skips the excluded paths', () => {
        startScan({startNode: LOCKS, checks: ['LockSanityCheck'], excludedPaths: [`${LOCKS}/inconsistent-lock/`]})
            .then(id => waitForExecution(id))
            .then(execution => {
                expect(execution.status).to.equal('finished');
                expect(execution.logs).to.include(`Skipping node ${LOCKS}/inconsistent-lock`);
                getErrors(execution.resultsID).then(errors => {
                    const paths = errors.map(e => e.nodePath);
                    expect(paths.filter(p => p.startsWith(`${LOCKS}/inconsistent-lock`))).to.be.empty;
                    expect(paths).to.include(`${LOCKS}/deletion-lock-on-translation/j:translation_en`);
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
        startScan({startNode: LOCKS, checks: ['LockSanityCheck'], upload: true}).then(id => waitForExecution(id)).then(execution => {
            expect(execution.status).to.equal('finished');
            expect(execution.reports.map(r => r.extension)).to.include.members(['csv']);
        });
    });

    it('answers for an unknown execution or unknown results', () => {
        readExecution('unknown-execution').then(execution => expect(execution.status).to.equal('Unknown execution ID'));
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
