import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoFix, fixAndVerify, fixFromTwoScans, graphql, runFixture, scan, ScanResults} from '../../support/integrity';

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
