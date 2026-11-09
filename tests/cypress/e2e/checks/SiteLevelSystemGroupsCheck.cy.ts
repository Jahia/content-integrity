import {createTestSite, deleteTestSite, expectExtraInfo, fixAndVerify, graphql, nodeExists, runFixture, scan} from '../../support/integrity';

const SITE = 'ciSystemGroupsCheck';
const SITE_PATH = `/sites/${SITE}`;
const CHECKS = ['SiteLevelSystemGroupsCheck'];

const SITE_PRIVILEGED = `${SITE_PATH}/groups/site-privileged`;

const scenario = (name: string) => runFixture('checks/SiteLevelSystemGroupsCheck.groovy', {SITEKEY: SITE, SCENARIO: name});
// The names of the members of a group, sorted
const membersOf = (groupPath: string): Cypress.Chainable<string[]> =>
    graphql('query($q: String!) { jcr { nodesByQuery(query: $q, queryLanguage: SQL2) { nodes { name } } } }',
        {q: `select * from [jnt:member] where isdescendantnode(['${groupPath}'])`})
        .then(data => data.jcr.nodesByQuery.nodes.map((n: { name: string }) => n.name).sort());
// The paths of the entries which grant a role to the group site-privileged in the site
const sitePrivilegedGrants = (): Cypress.Chainable<string[]> =>
    graphql('query($q: String!) { jcr { nodesByQuery(query: $q, queryLanguage: SQL2) { nodes { path } } } }',
        {q: `select * from [jnt:ace] where isdescendantnode(['${SITE_PATH}']) and localname() = 'GRANT_g_site-privileged'`})
        .then(data => data.jcr.nodesByQuery.nodes.map((n: { path: string }) => n.path).sort());
const roles = (acePath: string): Cypress.Chainable<string[]> =>
    graphql(`{ jcr { nodeByPath(path: "${acePath}") { property(name: "j:roles") { values } } } }`).its('jcr.nodeByPath.property.values');

describe('SiteLevelSystemGroupsCheck', () => {
    // The entries written with the site: on the site node, and on its folders users and groups when Jahia creates them
    let grantsAtCreation: string[];

    before(() => {
        createTestSite(SITE);
        sitePrivilegedGrants().then(grants => {
            grantsAtCreation = grants;
        });
    });

    after(() => {
        scenario('RESTORE');
        deleteTestSite(SITE);
    });

    it('does not report a site with its system groups', () => {
        scan(SITE_PATH, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === SITE_PATH)).to.have.length(0));
    });

    it('detects MISSING_MEMBERSHIP when the group site-privileged is not a member of privileged', () => {
        scenario('MISSING_MEMBERSHIP');
        scan(SITE_PATH, CHECKS).then(results => {
            const error = results.errors.find(e => e.errorType === 'MISSING_MEMBERSHIP');
            expectExtraInfo(error, 'missing-member', 'site-privileged');
            expectExtraInfo(error, 'group-missing-a-member', 'privileged');
        });
    });

    it('fixes MISSING_MEMBERSHIP by adding the group site-privileged to privileged', () => {
        fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'MISSING_MEMBERSHIP', SITE_PATH);
    });

    it('detects GROUP_DOES_NOT_EXIST when the group site-privileged of the site is deleted', () => {
        scenario('GROUP_DOES_NOT_EXIST');
        scan(SITE_PATH, CHECKS).then(results => {
            expectExtraInfo(results.errors.find(e => e.errorType === 'GROUP_DOES_NOT_EXIST'), 'group-name', 'site-privileged');
        });
        scenario('RESTORE');
    });

    it('fixes GROUP_DOES_NOT_EXIST by creating the group again, as with the site', () => {
        // The members and the grants of the group created with the site are the reference
        membersOf(SITE_PRIVILEGED).then(members => {
            expect(members, 'members of the group created with the site').to.not.be.empty;
            scenario('GROUP_DOES_NOT_EXIST');
            // Deleting the group leaves its entries: they are deleted too, so that the fix has to grant the role again
            grantsAtCreation.forEach(ace => graphql('mutation($p: String!) { jcr { deleteNode(pathOrId: $p) } }', {p: ace}));
            sitePrivilegedGrants().should('be.empty');
            fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'GROUP_DOES_NOT_EXIST', SITE_PATH);
            nodeExists(SITE_PRIVILEGED).should('be.true');
            membersOf(SITE_PRIVILEGED).should('deep.equal', members);
        });
        scan(SITE_PATH, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === SITE_PATH), 'errors on the site').to.have.length(0));
        cy.wrap(null).then(() => {
            expect(grantsAtCreation, 'entries of the group written with the site').to.include(`${SITE_PATH}/j:acl/GRANT_g_site-privileged`);
            sitePrivilegedGrants().should('include.members', grantsAtCreation);
            grantsAtCreation.forEach(ace => roles(ace).should('include', 'privileged'));
        });
    });

    // As the ACL listener, the fix reads the entries of the ACL only: an ACE created elsewhere grants nothing, and its
    // principal must not become a member of the group, which reads the unpublished content of the site
    it('does not add to the group created again the principal of an ACE outside of an ACL', () => {
        const outsider = `ci-${SITE}-outsider`;
        scenario('PLANTED_ACE');
        scenario('GROUP_DOES_NOT_EXIST');
        fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'GROUP_DOES_NOT_EXIST', SITE_PATH);
        membersOf(SITE_PRIVILEGED).should('not.be.empty').and('not.include', outsider);
    });
});
