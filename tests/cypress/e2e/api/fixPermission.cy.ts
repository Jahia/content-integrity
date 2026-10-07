import {createTestSite, deleteTestSite, graphql, runFixture, scan, ScanResults} from '../../support/integrity';
import {getResultsTable, getRow, openRowMenu, getMenuItem, clearFilters, visitAdmin} from '../../support/adminPage';

// The access to the module, adminContentIntegrity, allows to scan and to read the results. Fixing the errors writes to the
// repository with a system session, so it requires its own permission, adminContentIntegrityFix, which the first one
// does not grant.
const SITE = 'ciFixPermission';
const LOCKS = `/sites/${SITE}/contents/locks`;
const VIEWER = {username: `ci-${SITE}-viewer`, password: `ci-${SITE}-viewer-Pwd1!`};
const FIXER = {username: `ci-${SITE}-fixer`, password: `ci-${SITE}-fixer-Pwd1!`};

type User = { username: string; password: string };

const requestAs = (user: User, query: string, variables: Record<string, unknown> = {}) => cy.request({
    method: 'POST',
    url: '/modules/graphql',
    auth: user,
    headers: {Origin: Cypress.config().baseUrl},
    body: {query, variables}
}).its('body');

const FIX_ERROR = 'query($resultsId: String, $id: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $resultsId) { error: fixError(id: $id) { fixed } } } }';
const FIX_ALL = 'query($resultsId: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $resultsId) { fixAll: fixAllErrors { fixed } } } }';

const expectPermissionError = (body: any) => {
    expect(body.errors, 'GraphQL errors').to.have.length.greaterThan(0);
    expect(body.errors[0].message).to.contain('adminContentIntegrityFix');
};

describe('Permission to fix the errors', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('permissions/fixPermission.groovy', {SITEKEY: SITE});
    });

    beforeEach(() => {
        scan(LOCKS, ['LockSanityCheck']).then(r => {
            results = r;
        });
    });

    after(() => {
        runFixture('permissions/fixPermission-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('lets a user with the access to the module only read the results, and tells that the user can not fix them', () => {
        requestAs(VIEWER, 'query($id: String) { integrity: contentIntegrity { canFixErrors results: scanResultsDetails(id: $id) { errorCount } } }', {id: results.resultsId})
            .then(body => {
                expect(body.errors).to.be.undefined;
                expect(body.data.integrity.canFixErrors).to.be.false;
                expect(body.data.integrity.results.errorCount).to.equal(results.errors.length);
            });
    });

    it('does not let a user without the permission fix an error', () => {
        requestAs(VIEWER, FIX_ERROR, {resultsId: results.resultsId, id: results.errors[0].id}).then(expectPermissionError);
        requestAs(VIEWER, FIX_ALL, {resultsId: results.resultsId}).then(expectPermissionError);
        scan(LOCKS, ['LockSanityCheck']).then(after => expect(after.errors).to.have.length(results.errors.length));
    });

    it('lets a user with the permission fix the errors', () => {
        requestAs(FIXER, 'query { integrity: contentIntegrity { canFixErrors } }').then(body => expect(body.data.integrity.canFixErrors).to.be.true);
        requestAs(FIXER, FIX_ERROR, {resultsId: results.resultsId, id: results.errors[0].id}).then(body => {
            expect(body.errors).to.be.undefined;
            expect(body.data.integrity.results.error.fixed).to.be.true;
        });
        requestAs(FIXER, FIX_ALL, {resultsId: results.resultsId}).then(body => {
            expect(body.errors).to.be.undefined;
            expect(body.data.integrity.results.fixAll.fixed).to.equal(results.errors.length - 1);
        });
        scan(LOCKS, ['LockSanityCheck']).then(after => expect(after.errors).to.have.length(0));
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('lets root fix the errors', () => {
        graphql('query { integrity: contentIntegrity { canFixErrors } }').then(data => expect(data.integrity.canFixErrors).to.be.true);
    });

    it('offers no fix in the administration page to a user without the permission', () => {
        visitAdmin(VIEWER);
        clearFilters();
        getResultsTable().should('be.visible');
        cy.contains('button', 'Fix all').should('not.exist');
        openRowMenu(`${LOCKS}/inconsistent-lock`);
        getMenuItem('Error details');
        cy.contains('li.moonstone-menuItem', /^Fix/).should('not.exist');
        getMenuItem('Error details').click();
        cy.contains('[role=dialog]', 'Error details').within(() => {
            cy.contains('INCONSISTENT_LOCK').should('be.visible');
            cy.contains('button', /^Fix/).should('not.exist');
        });
    });

    it('offers the fix in the administration page to a user with the permission', () => {
        visitAdmin(FIXER);
        clearFilters();
        cy.contains('button', 'Fix all').should('be.visible');
        openRowMenu(`${LOCKS}/inconsistent-lock`);
        getMenuItem(/^Fix$/).click();
        getRow(`${LOCKS}/inconsistent-lock`).within(() => cy.contains('Fixed').should('be.visible'));
    });
});
