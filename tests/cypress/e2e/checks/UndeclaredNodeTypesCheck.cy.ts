import {createTestSite, deleteTestSite, expectError, expectExtraInfo, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

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
    });

    describe('Fix', () => {
        it('fixes UNDECLARED_NODE_TYPE on a mixin by removing the mixin', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-mixin`);
        });

        it('fixes UNDECLARED_NODE_TYPE on a primary type by removing the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-primary-type`);
        });

        it('fixes GHOST_NODE_TYPE by removing the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'GHOST_NODE_TYPE', `${ROOT}/ghost-type`);
        });
    });
});
