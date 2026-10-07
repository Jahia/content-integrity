import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectCheckEnabled,
    expectError,
    expectExtraInfo,
    expectNoError,
    fixAndVerify,
    resetCheckConfiguration,
    runFixture,
    scan
} from '../../support/integrity';

const SITE = 'ciVersionHistoryCheck';
const ROOT = `/sites/${SITE}/contents/version-history`;
const CHECK = 'VersionHistoryCheck';
const CHECKS = [CHECK];

describe('VersionHistoryCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/VersionHistoryCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
    });

    it('is disabled by default', () => {
        expectCheckEnabled(CHECK, false);
    });

    it('does not report a node with 4 versions with the default threshold', () => {
        scan(ROOT, CHECKS).then(results => expectNoError(results, 'TOO_MANY_VERSIONS', `${ROOT}/many-versions`));
    });

    describe('With a threshold of 2 versions', () => {
        before(() => configureCheck(CHECK, 'threshold', '2'));

        after(() => resetCheckConfiguration(CHECK));

        it('detects TOO_MANY_VERSIONS on a node with more versions than the threshold', () => {
            scan(ROOT, CHECKS).then(results => {
                expectExtraInfo(expectError(results, 'TOO_MANY_VERSIONS', `${ROOT}/many-versions`), 'versions-count', '4');
                expectNoError(results, 'TOO_MANY_VERSIONS', `${ROOT}/few-versions`);
            });
        });

        it('fixes TOO_MANY_VERSIONS by purging the version history', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'TOO_MANY_VERSIONS', `${ROOT}/many-versions`);
        });
    });
});
