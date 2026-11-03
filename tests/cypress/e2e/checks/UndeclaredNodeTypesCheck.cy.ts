import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixAndVerify, fixFromTwoScans, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciUndeclaredTypesCheck';
const ROOT = `/sites/${SITE}/contents/undeclared-node-types`;
const CHECKS = ['UndeclaredNodeTypesCheck'];

describe('UndeclaredNodeTypesCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/UndeclaredNodeTypesCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        runFixture('checks/UndeclaredNodeTypesCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    describe('Detection', () => {
        it('detects UNDECLARED_NODE_TYPE on a node whose primary type is not declared anymore', () => {
            expectExtraInfo(expectError(results, 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-primary-type`), 'primary type', 'jnt:ciTestUndeclared');
        });

        it('detects UNDECLARED_NODE_TYPE on a node with a mixin which is not declared anymore', () => {
            expectExtraInfo(expectError(results, 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-mixin`), 'mixin type', 'jmix:ciTestUndeclaredMixin');
        });

        it('detects GHOST_NODE_TYPE on a node whose type is not declared by its module', () => {
            expectExtraInfo(expectError(results, 'GHOST_NODE_TYPE', `${ROOT}/ghost-type`), 'primary type', 'jnt:ciTestGhost');
        });

        it('detects GHOST_NODE_TYPE on a node whose type is bound to a module which is not installed', () => {
            expectExtraInfo(expectError(results, 'GHOST_NODE_TYPE', `${ROOT}/orphan-type`), 'primary type', 'jnt:ciTestOrphan');
        });

        it('does not report a node of a type of Jahia core, nor of a type declared by a started module', () => {
            ['UNDECLARED_NODE_TYPE', 'GHOST_NODE_TYPE'].forEach(errorType => {
                expectNoError(results, errorType, ROOT);
                expectNoError(results, errorType, `${ROOT}/declared-type`);
            });
        });
    });

    describe('Fix', () => {
        it('fixes UNDECLARED_NODE_TYPE on a mixin by removing the mixin', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-mixin`);
        });

        it('reports an undeclared mixin already removed as fixed', () => {
            runFixture('checks/UndeclaredNodeTypesCheck.groovy', {SITEKEY: SITE});
            fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-mixin`);
        });

        it('fixes UNDECLARED_NODE_TYPE on a primary type by removing the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-primary-type`);
        });

        it('fixes GHOST_NODE_TYPE by removing the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'GHOST_NODE_TYPE', `${ROOT}/ghost-type`);
        });

        it('fixes GHOST_NODE_TYPE of a module which is not installed by removing the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'GHOST_NODE_TYPE', `${ROOT}/orphan-type`);
        });
    });
});
