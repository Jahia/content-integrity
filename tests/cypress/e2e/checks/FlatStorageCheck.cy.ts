import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectExtraInfo,
    expectNoError,
    expectNotFixable,
    resetCheckConfiguration,
    runFixture,
    scan
} from '../../support/integrity';

const SITE = 'ciFlatStorageCheck';
const ROOT = `/sites/${SITE}/contents/flat-storage`;
const CHECK = 'FlatStorageCheck';
const CHECKS = [CHECK];

describe('FlatStorageCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/FlatStorageCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
    });

    afterEach(() => resetCheckConfiguration(CHECK));

    it('does not report a node with 5 children with the default threshold', () => {
        scan(ROOT, CHECKS).then(results => expectNoError(results, 'TOO_MANY_CHILD_NODES', `${ROOT}/too-many-children`));
    });

    it('detects TOO_MANY_CHILD_NODES on a node with more children than the threshold', () => {
        configureCheck(CHECK, 'threshold', '3');
        scan(ROOT, CHECKS).then(results => {
            expectNotFixable(results, 'TOO_MANY_CHILD_NODES', `${ROOT}/too-many-children`);
            const error = results.errors.find(e => e.errorType === 'TOO_MANY_CHILD_NODES' && e.nodePath === `${ROOT}/too-many-children`);
            expectExtraInfo(error, 'children-count', '5');
            expectNoError(results, 'TOO_MANY_CHILD_NODES', `${ROOT}/few-children`);
        });
    });
});
