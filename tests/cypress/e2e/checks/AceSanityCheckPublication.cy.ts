import {createUser, deleteUser} from '@jahia/cypress';
import {createTestSite, deleteTestSite, expectError, fixError, requestAs, rootUser, runFixture, scan} from '../../support/integrity';

// A fix of AceSanityCheck publishes the ACL it changed only when the ACL was the same in both workspaces before the fix:
// a grant which awaits its publication is not published with it. See the fixtures script.
const SITE = 'ciAcePublication';
const HOME = `/sites/${SITE}/home`;
const CHECKS = ['AceSanityCheck'];
const MISSING_ROLE = 'ci-role-which-does-not-exist';
const user = (suffix: string) => `ci-${SITE}-${suffix}`;
const ace = (page: string, suffix: string) => `${HOME}/${page}/j:acl/GRANT_u_${user(suffix)}`;

// The roles of an ACE in a workspace, or null when the ACE does not exist there
const roles = (path: string, workspace: 'EDIT' | 'LIVE'): Cypress.Chainable<string[] | null> =>
    requestAs(rootUser(), 'query($path: String!, $workspace: Workspace!) { jcr(workspace: $workspace) { nodeByPath(path: $path) { property(name: "j:roles") { values } } } }',
        {path, workspace})
        .then(body => (body.data?.jcr?.nodeByPath ? body.data.jcr.nodeByPath.property?.values ?? [] : null));

const fixRoleDoesntExist = (page: string) => {
    scan(`${HOME}/${page}`, CHECKS).then(results => {
        const error = expectError(results, 'ROLE_DOESNT_EXIST', ace(page, 'a'));
        fixError(results.resultsId, error.id).its('fixed').should('equal', true);
    });
};

describe('AceSanityCheck: publication of the fixed ACL', () => {
    before(() => {
        createTestSite(SITE);
        ['a', 'b'].forEach(suffix => createUser(user(suffix), `${user(suffix)}-Pwd1!`));
        runFixture('checks/AceSanityCheck-publication.groovy', {SITEKEY: SITE});
    });

    after(() => {
        deleteTestSite(SITE);
        ['a', 'b'].forEach(suffix => deleteUser(user(suffix)));
    });

    it('publishes the fix of an ACL which was the same in both workspaces', () => {
        roles(ace('synced', 'a'), 'LIVE').should('include', MISSING_ROLE);
        fixRoleDoesntExist('synced');
        roles(ace('synced', 'a'), 'EDIT').should('deep.equal', ['editor']);
        roles(ace('synced', 'a'), 'LIVE').should('deep.equal', ['editor']);
    });

    it('does not publish an ACL which has another unpublished change, such as a grant which awaits its review', () => {
        roles(ace('pending', 'b'), 'EDIT').should('deep.equal', ['editor']);
        roles(ace('pending', 'b'), 'LIVE').should('equal', null);
        fixRoleDoesntExist('pending');
        roles(ace('pending', 'a'), 'EDIT').should('deep.equal', ['editor']);
        // Neither the fix nor the pending grant is published: the ACL is left to the publication of its node
        roles(ace('pending', 'a'), 'LIVE').should('include', MISSING_ROLE);
        roles(ace('pending', 'b'), 'LIVE').should('equal', null);
    });
});
