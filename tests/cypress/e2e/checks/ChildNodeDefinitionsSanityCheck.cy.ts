import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciChildNodeDefCheck';
const ROOT = `/sites/${SITE}/contents/child-node-definitions`;
const CHECKS = ['ChildNodeDefinitionsSanityCheck'];

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
});
