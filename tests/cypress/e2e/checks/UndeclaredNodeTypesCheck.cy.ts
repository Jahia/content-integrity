import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoError,
    expectNotFixable,
    fixAndVerify,
    fixError,
    fixFromTwoScans,
    nodeExists,
    resetCheckConfiguration,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciUndeclaredTypesCheck';
const ROOT = `/sites/${SITE}/contents/undeclared-node-types`;
const CHECK = 'UndeclaredNodeTypesCheck';
const CHECKS = [CHECK];
const TYPES_TO_FIX = 'jnt:ciTestUndeclared, jmix:ciTestUndeclaredMixin, jnt:ciTestGhost, jnt:ciTestOrphan, jnt:ciTestDeclaredAgain';

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
        resetCheckConfiguration(CHECK);
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

        it('counts the descendants and the references from outside the subtree, which the fix of a primary type deletes', () => {
            const orphan = expectError(results, 'GHOST_NODE_TYPE', `${ROOT}/orphan-type`);
            expectExtraInfo(orphan, 'descendants-count', '1');
            expectExtraInfo(orphan, 'references-count', '1');
            const undeclared = expectError(results, 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-primary-type`);
            expectExtraInfo(undeclared, 'descendants-count', '0');
            expectExtraInfo(undeclared, 'references-count', '0');
        });

        it('does not report a node of a type of Jahia core, nor of a type declared by a started module', () => {
            ['UNDECLARED_NODE_TYPE', 'GHOST_NODE_TYPE'].forEach(errorType => {
                expectNoError(results, errorType, ROOT);
                expectNoError(results, errorType, `${ROOT}/declared-type`);
            });
        });
    });

    describe('Fix', () => {
        it('fixes no error while the configuration names no type', () => {
            expectNotFixable(results, 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-primary-type`);
            expectNotFixable(results, 'UNDECLARED_NODE_TYPE', `${ROOT}/undeclared-mixin`);
            expectNotFixable(results, 'GHOST_NODE_TYPE', `${ROOT}/ghost-type`);
            expectNotFixable(results, 'GHOST_NODE_TYPE', `${ROOT}/orphan-type`);
        });

        describe('With the types named in the configuration', () => {
            before(() => {
                configureCheck(CHECK, 'types-to-fix', TYPES_TO_FIX);
            });

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

            it('keeps the node when its type is declared again after the scan', () => {
                scan(ROOT, CHECKS).then(r => {
                    const error = expectError(r, 'UNDECLARED_NODE_TYPE', `${ROOT}/declared-again`);
                    runFixture('checks/UndeclaredNodeTypesCheck-declareAgain.groovy');
                    fixError(r.resultsId, error.id).then(result => {
                        expect(result.fixed, `Fix of UNDECLARED_NODE_TYPE on ${error.nodePath}${result.message ? ': ' + result.message : ''}`).to.be.true;
                    });
                });
                nodeExists(`${ROOT}/declared-again`).then(exists => expect(exists, `${ROOT}/declared-again is kept`).to.be.true);
                scan(ROOT, CHECKS).then(r => expectNoError(r, 'UNDECLARED_NODE_TYPE', `${ROOT}/declared-again`));
            });
        });
    });
});
