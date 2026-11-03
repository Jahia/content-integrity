import {createUser, deleteUser, grantRoles} from '@jahia/cypress';
import {
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoFix,
    fixAndVerify,
    fixError,
    fixFromTwoScans,
    graphql,
    requestAs,
    rootUser,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciPublicationDefaultCheck';
const ROOT = `/sites/${SITE}/contents/publication-default`;
const CHECKS = ['PublicationSanityDefaultCheck'];

describe('PublicationSanityDefaultCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/PublicationSanityDefaultCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    describe('Detection', () => {
        it('detects NO_LIVE_NODE on a node flagged as published, which has never been published', () => {
            expectError(results, 'NO_LIVE_NODE', `${ROOT}/flagged-as-published`);
        });

        it('detects NO_LIVE_NODE on an auto-published node which is not in live', () => {
            expectError(results, 'NO_LIVE_NODE', `${ROOT}/auto-published-reference`);
        });

        it('detects DIFFERENT_PATH on a published node moved in live', () => {
            const error = expectError(results, 'DIFFERENT_PATH', `${ROOT}/moved-in-live`);
            expectExtraInfo(error, 'live-node-path', `${ROOT}/live-destination/moved-in-live`);
        });

        it('detects DIFFERENT_PATH_POTENTIAL_FP when the moved node is the root of the scan', () => {
            scan(`${ROOT}/moved-in-live`, CHECKS).then(r => expectError(r, 'DIFFERENT_PATH_POTENTIAL_FP', `${ROOT}/moved-in-live`));
        });

        it('detects PATH_CONFLICT on a node whose live path is used by another node', () => {
            expectExtraInfo(expectError(results, 'PATH_CONFLICT', `${ROOT}/path-conflict`), 'conflicted-edit-node', 'none');
        });

        it('detects DIFFERENT_PT on a published node whose live node has another primary type', () => {
            expectExtraInfo(expectError(results, 'DIFFERENT_PT', `${ROOT}/different-primary-type`), 'live-node-primary-type', 'jnt:bigText');
        });
    });

    describe('Fix', () => {
        ['DIFFERENT_PATH:moved-in-live', 'PATH_CONFLICT:path-conflict', 'DIFFERENT_PT:different-primary-type'].forEach(entry => {
            const [errorType, name] = entry.split(':');
            it(`provides no fix for ${errorType}`, () => {
                expectNoFix(ROOT, CHECKS, 'EDIT', errorType, `${ROOT}/${name}`);
            });
        });

        it('provides no fix for DIFFERENT_PATH_POTENTIAL_FP', () => {
            expectNoFix(`${ROOT}/moved-in-live`, CHECKS, 'EDIT', 'DIFFERENT_PATH_POTENTIAL_FP', `${ROOT}/moved-in-live`);
        });

        it('fixes NO_LIVE_NODE on an auto-published node by publishing it without the content it references', () => {
            const nodeInLive = (path: string): Cypress.Chainable<boolean> =>
                requestAs(rootUser(), 'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { uuid } } }', {path})
                    .then(body => Boolean(body.data?.jcr?.nodeByPath));
            nodeInLive(`${ROOT}/auto-published-reference`).should('be.false');
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'NO_LIVE_NODE', `${ROOT}/auto-published-reference`);
            nodeInLive(`${ROOT}/auto-published-reference`).should('be.true');
            nodeInLive(`${ROOT}/referenced-draft`).should('be.false');
        });

        it('fixes NO_LIVE_NODE by removing the publication flag', () => {
            // Fixed from the first results, then from the second ones, where it is already fixed
            fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'NO_LIVE_NODE', `${ROOT}/flagged-as-published`);
        });
    });
});

describe('PublicationSanityDefaultCheck on the content of a module', () => {
    const MODULE_SITE = 'ciPublicationModules';
    const nodeInLive = (path: string): Cypress.Chainable<boolean> =>
        graphql('query($p: String!) { jcr(workspace: LIVE) { nodesByQuery(query: $p, queryLanguage: SQL2) { nodes { path } } } }',
            {p: `select * from [jnt:contentFolder] where issamenode(['${path}'])`})
            .then(data => data.jcr.nodesByQuery.nodes.length > 0);
    let folder: string;

    before(() => {
        runFixture('checks/PublicationSanityDefaultCheck-modules.groovy', {SITEKEY: MODULE_SITE});
        graphql(`{ jcr { nodesByQuery(query: "select * from [jnt:contentFolder] where localname() = 'ci-unpublished-${MODULE_SITE}' and isdescendantnode('/modules/templates-system')", queryLanguage: SQL2) { nodes { path } } } }`)
            .then(data => {
                folder = data.jcr.nodesByQuery.nodes[0].path;
            });
    });

    after(() => runFixture('checks/PublicationSanityDefaultCheck-cleanup.groovy', {SITEKEY: MODULE_SITE}));

    it('detects NO_LIVE_NODE on the content of a module which is not in live', () => {
        cy.wrap(null).then(() => scan(folder, CHECKS).then(r => expectError(r, 'NO_LIVE_NODE', folder)));
    });

    it('fixes NO_LIVE_NODE on the content of a module by publishing it, as the deployment of the module does', () => {
        cy.wrap(null).then(() => {
            nodeInLive(folder).should('be.false');
            fixAndVerify(folder, CHECKS, 'EDIT', 'NO_LIVE_NODE', folder);
            nodeInLive(folder).should('be.true');
        });
    });
});

// An editor who may not publish flags their node j:published, and makes it auto-published after the scan. The fix must not
// publish, as root, the unpublished content of another editor which the node references.
describe('PublicationSanityDefaultCheck: NO_LIVE_NODE on a node which references unpublished content', () => {
    const REF_SITE = 'ciPublicationReferences';
    const HOME = `/sites/${REF_SITE}/home`;
    const EDITOR_A = {username: `ci-${REF_SITE}-a`, password: `ci-${REF_SITE}-a-Pwd1!`};
    const EDITOR_B = {username: `ci-${REF_SITE}-b`, password: `ci-${REF_SITE}-b-Pwd1!`};
    const ADD_NODE = `mutation($parent: String!, $name: String!, $type: String!, $props: [InputJCRProperty]) {
        jcr { addNode(parentPathOrId: $parent, name: $name, primaryNodeType: $type, properties: $props) { uuid } } }`;
    const PUBLISH = 'mutation($path: String!) { jcr { mutateNode(pathOrId: $path) { publish(languages: ["en"]) } } }';
    const inLive = (path: string): Cypress.Chainable<boolean> =>
        requestAs(rootUser(), 'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { uuid } } }', {path})
            .then(body => Boolean(body.data?.jcr?.nodeByPath));
    const waitForLive = (path: string, attempt = 0): void => {
        inLive(path).then(live => {
            if (live) return;
            expect(attempt, `${path} published`).to.be.lessThan(30);
            cy.wait(1000);
            waitForLive(path, attempt + 1);
        });
    };
    const page = (name: string) => ({parent: HOME, name, type: 'jnt:page', props: [
        {name: 'jcr:title', value: name, language: 'en'}, {name: 'j:templateName', value: 'home'}]});

    before(() => {
        createTestSite(REF_SITE);
        graphql(ADD_NODE, page('sectiona'));
        graphql(ADD_NODE, page('sectionb'));
        [HOME, `${HOME}/sectiona`, `${HOME}/sectionb`].forEach(path => {
            graphql(PUBLISH, {path});
            waitForLive(path);
        });
        createUser(EDITOR_A.username, EDITOR_A.password);
        createUser(EDITOR_B.username, EDITOR_B.password);
        grantRoles(`${HOME}/sectiona`, ['editor'], EDITOR_A.username, 'USER');
        grantRoles(`${HOME}/sectionb`, ['editor'], EDITOR_B.username, 'USER');
    });

    after(() => {
        deleteTestSite(REF_SITE);
        deleteUser(EDITOR_A.username);
        deleteUser(EDITOR_B.username);
    });

    it('does not publish the referenced content of another editor', () => {
        const x = `${HOME}/sectiona/x`;
        const r = `${HOME}/sectionb/r`;
        requestAs(EDITOR_B, ADD_NODE, {parent: `${HOME}/sectionb`, name: 'r', type: 'jnt:text', props: [{name: 'text', value: 'draft of B', language: 'en'}]})
            .its('data.jcr.addNode.uuid')
            .then(uuid => requestAs(EDITOR_A, ADD_NODE, {parent: `${HOME}/sectiona`, name: 'x', type: 'jnt:contentReference', props: [{name: 'j:node', value: uuid}]}));
        requestAs(EDITOR_A, 'mutation($path: String!) { jcr { mutateNode(pathOrId: $path) { mutateProperty(name: "j:published") { setValue(value: "true", type: BOOLEAN) } } } }', {path: x});
        scan(`${HOME}/sectiona`, CHECKS).then(results => {
            const error = expectError(results, 'NO_LIVE_NODE', x);
            requestAs(EDITOR_A, 'mutation($path: String!) { jcr { mutateNode(pathOrId: $path) { addMixins(mixins: ["jmix:autoPublish"]) } } }', {path: x});
            // Control: the auto-publication by the platform publishes the node alone
            waitForLive(x);
            inLive(r).should('be.false');
            fixError(results.resultsId, error.id).its('fixed').should('equal', true);
        });
        inLive(x).should('be.true');
        inLive(r).should('be.false');
    });
});
