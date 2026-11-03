import {createTestSite, deleteTestSite, expectCheckEnabled, expectExtraInfo, expectNotFixable, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciVersionSanityCheck';
const CHECK = 'VersionSanityCheck';
const CHECKS = [CHECK];
// The node of the fixtures is created with a known identifier, which gives the path of its version history
const ROOT = '/jcr:system/jcr:versionStorage/0c/1e/57';
const HISTORY = `${ROOT}/0c1e57ed-0000-4000-a000-000000000001`;

describe('VersionSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/VersionSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        deleteTestSite(SITE);
        runFixture('checks/VersionSanityCheck-cleanup.groovy', {SITEKEY: SITE});
    });

    it('is disabled by default', () => {
        expectCheckEnabled(CHECK, false);
    });

    it('detects ORPHANED_HISTORY on the version history of a deleted node', () => {
        expectNotFixable(results, 'ORPHANED_HISTORY', HISTORY);
    });

    it('detects ORPHAN_IN_SUBTREE on the root of the scan', () => {
        expectNotFixable(results, 'ORPHAN_IN_SUBTREE', ROOT);
        expectExtraInfo(results.errors.find(e => e.errorType === 'ORPHAN_IN_SUBTREE'), 'orphaned-version-histories-count', '1');
    });

    // jcr:versionableUuid is protected and set by the version manager
    it.skip('detects HISTORY_WITHOUT_NODE_ID (not reproducible)');
});
