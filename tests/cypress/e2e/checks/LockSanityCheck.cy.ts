import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciLockCheck';
const ROOT = `/sites/${SITE}/contents/locks`;
const CHECKS = ['LockSanityCheck'];

describe('LockSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('detects INCONSISTENT_LOCK on a node with only some of the lock properties', () => {
        const error = expectError(results, 'INCONSISTENT_LOCK', `${ROOT}/inconsistent-lock`);
        expectExtraInfo(error, 'missing-properties', /j:locktoken/);
    });

    it('detects DELETION_LOCK_ON_I18N on a translation node locked for deletion', () => {
        expectError(results, 'DELETION_LOCK_ON_I18N', `${ROOT}/deletion-lock-on-translation/j:translation_en`);
    });

    it('does not report a node which is not locked', () => {
        expectNoError(results, 'INCONSISTENT_LOCK', `${ROOT}/not-locked`);
    });

    it('fixes INCONSISTENT_LOCK', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'INCONSISTENT_LOCK', `${ROOT}/inconsistent-lock`);
    });

    it('fixes DELETION_LOCK_ON_I18N', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'DELETION_LOCK_ON_I18N', `${ROOT}/deletion-lock-on-translation/j:translation_en`);
    });
});
