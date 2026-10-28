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

// The deletion of the stored results of a scan: its report node, with its errors, its log and its CSV and XLSX reports.
// It requires the permission adminContentIntegrityDelete, which only the role server-administrator holds, and is done
// with the session of the current user, so that only the users who may read the reports delete them.
const SITE = 'ciDeleteResults';
const LOCKS = `/sites/${SITE}/contents/locks`;
const VIEWER = {username: `ci-${SITE}-viewer`, password: `ci-${SITE}-viewer-Pwd1!`};
const FIXER = {username: `ci-${SITE}-fixer`, password: `ci-${SITE}-fixer-Pwd1!`};

const DELETE = 'query($id: String!) { integrity: contentIntegrity { deleted: deleteScanResults(id: $id) } }';

const requestAs = (user: { username: string; password: string }, query: string, variables: Record<string, unknown> = {}) => cy.request({
    method: 'POST',
    url: '/modules/graphql',
    auth: user,
    headers: {Origin: Cypress.config().baseUrl},
    body: {query, variables}
}).its('body');

const isListed = (resultsId: string): Cypress.Chainable<boolean> =>
    graphql('{ integrity: contentIntegrity { summaries: scanResultsSummaries { id } } }')
        .then(data => data.integrity.summaries.some((s: { id: string }) => s.id === resultsId));

const scanLocks = (): Cypress.Chainable<string> => scan(LOCKS, ['LockSanityCheck']).then(results => results.resultsId as string);

describe('Deletion of the results of a scan', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('permissions/fixPermission.groovy', {SITEKEY: SITE});
    });

    after(() => {
        runFixture('permissions/fixPermission-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('deletes the results, with their errors, their log and their reports', () => {
        scanLocks().then(resultsId => {
            readStoredReport(resultsId).then(report => {
                expect(report?.files.some(f => f.endsWith('.csv')), 'CSV report').to.be.true;
                expect(report?.files.some(f => f.endsWith('.xlsx')), 'XLSX report').to.be.true;
            });
            graphql(DELETE, {id: resultsId}).its('integrity.deleted').should('equal', true);
            readStoredReport(resultsId).should('be.null');
            isListed(resultsId).should('be.false');
            // The results were read by the scan: they are not served from memory afterwards
            graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { totalErrorCount } } }', {id: resultsId})
                .its('integrity.results').should('be.null');
            graphql('query($id: String!) { integrity: contentIntegrity { logs: scanResultsLogs(id: $id) } }', {id: resultsId})
                .its('integrity.logs').should('be.null');
        });
    });

    it('returns false for unknown results', () => {
        graphql(DELETE, {id: 'default_2020_01_01-00_00_00_000'}).its('integrity.deleted').should('equal', false);
    });

    it('does not delete the results of a running scan', () => {
        registerSlowCheck();
        startScan(SLOW_SCAN).then(id => readExecution(id)).then(execution => {
            const resultsId = execution.resultsID as string;
            requestAs({username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')}, DELETE, {id: resultsId}).then(body => {
                expect(body.errors, 'GraphQL errors').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('running scan');
            });
            readStoredReport(resultsId).should('not.be.null');
        });
        stopRunningScan();
        unregisterSlowCheck();
    });

    it('tells who may delete the results', () => {
        graphql('{ integrity: contentIntegrity { canDeleteResults } }').its('integrity.canDeleteResults').should('equal', true);
        requestAs(VIEWER, '{ integrity: contentIntegrity { canDeleteResults } }').its('data.integrity.canDeleteResults').should('equal', false);
        // The fix of the errors does not grant the deletion of the results
        requestAs(FIXER, '{ integrity: contentIntegrity { canDeleteResults } }').its('data.integrity.canDeleteResults').should('equal', false);
    });

    it('does not let a user without the permission adminContentIntegrityDelete delete all the results', () => {
        scanLocks().then(resultsId => {
            requestAs(VIEWER, '{ integrity: contentIntegrity { deleteAllScanResults } }').then(body => {
                expect(body.errors, 'GraphQL errors').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('adminContentIntegrityDelete');
            });
            readStoredReport(resultsId).should('not.be.null');
            graphql(DELETE, {id: resultsId});
        });
    });

    it('does not let a user without the permission adminContentIntegrityDelete delete the results', () => {
        scanLocks().then(resultsId => {
            requestAs(VIEWER, DELETE, {id: resultsId}).then(body => {
                expect(body.errors, 'GraphQL errors').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('adminContentIntegrityDelete');
            });
            readStoredReport(resultsId).should('not.be.null');
            isListed(resultsId).should('be.true');
            graphql(DELETE, {id: resultsId});
        });
    });
});
