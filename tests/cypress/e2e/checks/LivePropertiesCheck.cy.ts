import {createTestSite, deleteTestSite, expectCheckEnabled, expectError, expectExtraInfo, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciLivePropertiesCheck';
const ROOT = `/sites/${SITE}/contents/live-properties`;
const CHECK = 'LivePropertiesCheck';
const CHECKS = [CHECK];

describe('LivePropertiesCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LivePropertiesCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('is disabled by default', () => {
        expectCheckEnabled(CHECK, false);
    });

    it('detects LIVE_PROPERTIES on a node with a value for j:liveProperties', () => {
        expectExtraInfo(expectError(results, 'LIVE_PROPERTIES', `${ROOT}/with-value`), 'live-properties', /text/);
    });

    it('detects EMPTY_LIVE_PROPERTIES on a node without j:liveProperties', () => {
        expectError(results, 'EMPTY_LIVE_PROPERTIES', `${ROOT}/without-value`);
    });

    it('fixes LIVE_PROPERTIES', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'LIVE_PROPERTIES', `${ROOT}/with-value`);
    });

    it('fixes EMPTY_LIVE_PROPERTIES', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'EMPTY_LIVE_PROPERTIES', `${ROOT}/without-value`);
    });
});
