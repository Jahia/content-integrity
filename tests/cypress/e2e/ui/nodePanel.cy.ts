import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {clearFilters, getDetailsPanel, getRow, openErrorDetails, selectInDropdown, visitAdmin} from '../../support/adminPage';

// The panel displays the errors of a node in one workspace. The fixtures give an error in each workspace.
const SITE = 'ciUiNodePanel';
const ROOT = `/sites/${SITE}/contents/mark-for-deletion`;
// NO_ROOT_DELETION, in the default workspace
const IN_DEFAULT = `${ROOT}/no-root-deletion`;
// DELETION_MARK_IN_LIVE, in the live workspace
const IN_LIVE = `${ROOT}/deleted-in-live`;

describe('Panel of the errors of a node', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, ['MarkForDeletionCheck'], 'BOTH');
    });

    after(() => {
        deleteTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck-cleanup.groovy', {SITEKEY: SITE});
    });

    beforeEach(() => {
        visitAdmin();
        clearFilters();
    });

    it('displays the errors of the node in the workspace of the clicked row only', () => {
        openErrorDetails(IN_LIVE);
        getDetailsPanel().should('contain.text', 'Workspace live').and('contain.text', 'DELETION_MARK_IN_LIVE').and('not.contain.text', 'NO_ROOT_DELETION');
        openErrorDetails(IN_DEFAULT);
        getDetailsPanel().should('contain.text', 'Workspace default').and('contain.text', 'NO_ROOT_DELETION').and('not.contain.text', 'DELETION_MARK_IN_LIVE');
    });

    it('closes the panel when the table is filtered on another workspace', () => {
        openErrorDetails(IN_DEFAULT);
        // Filtered on the workspace of the node, the panel stays
        selectInDropdown('Workspace', /^default \(\d+\)$/);
        getDetailsPanel().should('contain.text', 'Workspace default');
        selectInDropdown('Workspace', /^live \(\d+\)$/);
        cy.get('#ci-node-errors').should('not.exist');
        getRow(IN_LIVE).should('exist');
    });
});
