import {
    createTestSite,
    deleteTestSite,
    expectCheckEnabled,
    expectError,
    expectExtraInfo,
    fixAndVerify,
    graphql,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciNodeNameInfoCheck';
const ROOT = `/sites/${SITE}/contents/node-name-info`;
const CHECK = 'NodeNameInfoSanityCheck';
const CHECKS = [CHECK];

describe('NodeNameInfoSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/NodeNameInfoSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        runFixture('checks/NodeNameInfoSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('is disabled by default', () => {
        expectCheckEnabled(CHECK, false);
    });

    it('detects INVALID_FULLPATH on a node whose j:fullpath is not its path', () => {
        const error = expectError(results, 'INVALID_FULLPATH', `${ROOT}/invalid-fullpath`);
        expectExtraInfo(error, 'property-value', `/sites/${SITE}/contents/somewhere-else`);
        expectExtraInfo(error, 'expected-property-value', `${ROOT}/invalid-fullpath`);
    });

    it('detects MISSING_NODENAME on a node without j:nodename', () => {
        expectError(results, 'MISSING_NODENAME', `${ROOT}/missing-nodename`);
    });

    it('detects INVALID_NODENAME on a node whose j:nodename is not its name', () => {
        expectExtraInfo(expectError(results, 'INVALID_NODENAME', `${ROOT}/invalid-nodename`), 'property-value', 'another-name');
    });

    it('does not check j:nodename under /modules, but checks j:fullpath', () => {
        // The template of the fixture, in the module folder of the running version
        graphql(`{ jcr { nodesByQuery(query: "select * from [jnt:template] where localname() = 'ci-node-name-${SITE}' and isdescendantnode('/modules/content-integrity')") { nodes { path checked: isNodeType(type: {types: ["jmix:nodenameInfo"]}) } } } }`)
            .its('jcr.nodesByQuery.nodes').then((nodes: { path: string, checked: boolean }[]) => {
                expect(nodes, 'template of the fixture').to.have.length(1);
                // The check applies to the template, out of /modules
                expect(nodes[0].checked, 'jmix:nodenameInfo').to.be.true;
                const path = nodes[0].path;
                scan(path, CHECKS).then(r => {
                    expect(r.errors.filter(e => e.nodePath === path).map(e => e.errorType)).to.deep.equal(['INVALID_FULLPATH']);
                });
            });
    });

    ['INVALID_FULLPATH:invalid-fullpath', 'MISSING_NODENAME:missing-nodename', 'INVALID_NODENAME:invalid-nodename'].forEach(entry => {
        const [errorType, name] = entry.split(':');
        it(`fixes ${errorType}`, () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', errorType, `${ROOT}/${name}`);
        });
    });
});
