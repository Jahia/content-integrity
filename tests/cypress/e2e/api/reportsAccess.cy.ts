import {createTestSite, deleteTestSite, graphql, runFixture} from '../../support/integrity';

// The reports describe the whole scanned repository: they are readable by the server administrators only.
// They are stored under the system site, which every editor of every site reads in the default workspace.
const SITE = 'ciReportsAccess';
const EDITOR = {username: `ci-${SITE}-editor`, password: `ci-${SITE}-editor-Pwd1!`};
const SERVER_ADMIN = {username: `ci-${SITE}-server-admin`, password: `ci-${SITE}-server-admin-Pwd1!`};

const waitForReport = (id: string, attempt = 0): Cypress.Chainable<string> =>
    graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { status reports { uri extension } } } }', {id})
        .then(data => {
            const {status, reports} = data.integrity.scan;
            if (status === 'running') {
                expect(attempt, 'The scan is still running').to.be.lessThan(120);
                cy.wait(500, {log: false});
                return waitForReport(id, attempt + 1);
            }

            expect(status).to.equal('finished');
            return cy.wrap(reports.find((r: { extension: string }) => r.extension === 'csv').uri as string, {log: false});
        });

const download = (uri: string, auth?: { username: string; password: string }) => cy.request({
    url: `/files/default${encodeURI(uri)}`,
    auth,
    failOnStatusCode: false
});

describe('Access to the scan reports', () => {
    let reportUri: string;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('reports/reportsAccess.groovy', {SITEKEY: SITE});
        graphql('query($path: String) { integrity: contentIntegrity { scan: integrityScan { id: scan(workspace: EDIT, startNode: $path, checksToRun: ["LockSanityCheck"], uploadResults: true) } } }',
            {path: `/sites/${SITE}/contents/locks`})
            .then(data => waitForReport(data.integrity.scan.id))
            .then(uri => {
                reportUri = uri;
            });
    });

    after(() => {
        runFixture('reports/reportsAccess-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('stores the reports under the system site', () => {
        expect(reportUri).to.match(/^\/sites\/systemsite\/files\/content-integrity-reports\//);
    });

    it('lets root download a report', () => {
        download(reportUri, {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')}).its('status').should('eq', 200);
    });

    it('lets a server administrator who is not root download a report', () => {
        download(reportUri, SERVER_ADMIN).its('status').should('eq', 200);
    });

    it('does not let an editor download a report, even about the site the editor works on', () => {
        download(reportUri, EDITOR).its('status').should('eq', 404);
    });

    it('does not let an editor download the stored errors of a scan, next to its reports', () => {
        // The CSV report is named <resultsId>-full.csv, the stored errors <resultsId>-full-errors.json.gz
        const errorsUri = reportUri.replace(/\.csv$/, '-errors.json.gz');
        download(errorsUri, {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')}).its('status').should('eq', 200);
        download(errorsUri, EDITOR).its('status').should('eq', 404);
    });

    it('does not let an editor list the reports', () => {
        cy.request({
            method: 'POST',
            url: '/modules/graphql',
            auth: EDITOR,
            headers: {Origin: Cypress.config().baseUrl},
            body: {query: '{ jcr(workspace: EDIT) { nodesByQuery(query: "select * from [jnt:file] where isdescendantnode([/sites/systemsite/files/content-integrity-reports])") { nodes { path } } } }'}
        }).then(response => expect(response.body.data.jcr.nodesByQuery.nodes).to.have.length(0));
    });

    it('does not let guest download a report', () => {
        download(reportUri).its('status').should('eq', 404);
    });
});
