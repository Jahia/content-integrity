import {breakAclInheritance, createUser, deleteUser, grantRoles} from '@jahia/cypress';
import {
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectFixFails,
    expectNoError,
    fixAndVerify,
    graphql,
    nodeExists,
    requestAs,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciChildNodeDefCheck';
const ROOT = `/sites/${SITE}/contents/child-node-definitions`;
const CHECKS = ['ChildNodeDefinitionsSanityCheck'];
const HOME = `/sites/${SITE}/home`;
const EDITOR = {username: `ci-${SITE}-editor`, password: `ci-${SITE}-editor-Pwd1!`};
const OTHER = {username: `ci-${SITE}-other`, password: `ci-${SITE}-other-Pwd1!`};
const ADD_PAGE = `mutation($parent: String!, $name: String!) { jcr { addNode(parentPathOrId: $parent, name: $name, primaryNodeType: "jnt:page",
    properties: [{name: "jcr:title", value: $name, language: "en"}, {name: "j:templateName", value: "home"}]) { uuid } } }`;

describe('ChildNodeDefinitionsSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/ChildNodeDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        runFixture('checks/ChildNodeDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
        deleteUser(EDITOR.username);
        deleteUser(OTHER.username);
    });

    it('detects NOT_ALLOWED_BY_PARENT_DEF on a node that its parent does not accept anymore', () => {
        expectExtraInfo(expectError(results, 'NOT_ALLOWED_BY_PARENT_DEF', `${ROOT}/not-allowed`), 'parent-node-type', 'jnt:contentFolder');
    });

    it('does not report a node accepted by its parent', () => {
        expectNoError(results, 'NOT_ALLOWED_BY_PARENT_DEF', `${ROOT}/allowed`);
    });

    it('fixes NOT_ALLOWED_BY_PARENT_DEF by deleting the node', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'NOT_ALLOWED_BY_PARENT_DEF', `${ROOT}/not-allowed`);
    });

    it('does not delete a node which has sub-nodes', () => {
        expectFixFails(ROOT, CHECKS, 'EDIT', 'NOT_ALLOWED_BY_PARENT_DEF', `${ROOT}/not-allowed-with-children`);
        nodeExists(`${ROOT}/not-allowed-with-children/kept`).should('be.true');
    });

    // A page has the named child definition j:conditionalVisibility, of another type. Jahia rejects a page with that
    // name, but the repository accepts it through the residual definition of the pages, so an editor can rename to it
    // a page they may not delete, because it holds a page they may not read.
    it('does not report a page which an editor renamed j:conditionalVisibility, and keeps its subpages', () => {
        graphql(ADD_PAGE, {parent: HOME, name: 'section'});
        graphql(ADD_PAGE, {parent: `${HOME}/section`, name: 'restricted'});
        createUser(EDITOR.username, EDITOR.password);
        createUser(OTHER.username, OTHER.password);
        grantRoles(`/sites/${SITE}`, ['editor'], EDITOR.username, 'USER');
        breakAclInheritance(`${HOME}/section/restricted`);
        grantRoles(`${HOME}/section/restricted`, ['editor'], OTHER.username, 'USER');

        requestAs(EDITOR, 'mutation($path: String!) { jcr { deleteNode(pathOrId: $path) } }', {path: `${HOME}/section`})
            .its('errors').should('not.be.empty');
        requestAs(EDITOR, 'mutation($path: String!) { jcr { mutateNode(pathOrId: $path) { rename(name: "j:conditionalVisibility") } } }', {path: `${HOME}/section`})
            .its('data.jcr.mutateNode.rename').should('equal', `${HOME}/j:conditionalVisibility`);

        scan(HOME, CHECKS).then(r => expectNoError(r, 'NOT_ALLOWED_BY_PARENT_DEF', `${HOME}/j:conditionalVisibility`));
        nodeExists(`${HOME}/j:conditionalVisibility/restricted`).should('be.true');
    });
});
