import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {clearFilters, getDetailsPanel, getDetailsWorkspace, getRow, openErrorDetails, selectInDropdown, visitLatestResults} from '../../support/adminPage';

// The panel displays the errors of a node in one workspace. The fixtures give an error in each workspace.
const SITE = 'ciUiNodePanel';
const ROOT = `/sites/${SITE}/contents/mark-for-deletion`;
// NO_ROOT_DELETION, in the default workspace
const IN_DEFAULT = `${ROOT}/no-root-deletion`;
// DELETION_MARK_IN_LIVE, in the live workspace
const IN_LIVE = `${ROOT}/deleted-in-live`;
// UNDEPLOYED_MODULE_ON_SITE, which depends on the modules deployed on the server, in the default workspace
const SITE_PATH = `/sites/${SITE}`;

describe('Panel of the errors of a node', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/UndeployedModulesReferencesCheck.groovy', {SITEKEY: SITE});
        scan(SITE_PATH, ['MarkForDeletionCheck', 'UndeployedModulesReferencesCheck'], 'BOTH');
    });

    after(() => {
        deleteTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck-cleanup.groovy', {SITEKEY: SITE});
    });

    beforeEach(() => {
        visitLatestResults();
        clearFilters();
    });

    it('displays the errors of the node in the workspace of the clicked row only', () => {
        openErrorDetails(IN_LIVE);
        getDetailsWorkspace().should('have.text', 'live');
        getDetailsPanel().should('contain.text', 'DELETION_MARK_IN_LIVE').and('not.contain.text', 'NO_ROOT_DELETION');
        openErrorDetails(IN_DEFAULT);
        getDetailsWorkspace().should('have.text', 'default');
        getDetailsPanel().should('contain.text', 'NO_ROOT_DELETION').and('not.contain.text', 'DELETION_MARK_IN_LIVE');
    });

    it('describes what the fix of each error changes, above its Fix button', () => {
        openErrorDetails(IN_DEFAULT);
        getDetailsPanel().find('[data-fix-description]').should('have.length', 1)
            .and('have.text', 'Removes the deletion mark of the node: its deletion is canceled.');
        openErrorDetails(IN_LIVE);
        getDetailsPanel().find('[data-fix-description]').should('have.text', 'Removes the deletion mark of the node in the live workspace.');
    });

    it('tells what a cluster changes to an error which depends on a resource of the server, and only to such an error', () => {
        openErrorDetails(SITE_PATH);
        getDetailsPanel().find('[data-cluster-helper]').should('have.length', 1)
            .and('contain.text', 'In a cluster')
            .and('contain.text', 'The module is not deployed on the server which ran the scan.');
        openErrorDetails(IN_DEFAULT);
        getDetailsPanel().find('[data-cluster-helper]').should('not.exist');
    });

    it('closes the panel when the table is filtered on another workspace', () => {
        openErrorDetails(IN_DEFAULT);
        // Filtered on the workspace of the node, the panel stays
        selectInDropdown('Workspace', /^default \(\d+\)$/);
        getDetailsWorkspace().should('have.text', 'default');
        selectInDropdown('Workspace', /^live \(\d+\)$/);
        cy.get('#ci-node-errors').should('not.exist');
        getRow(IN_LIVE).should('exist');
    });
});
