import {createTestSite, deleteTestSite, expectCheckEnabled, expectExtraInfo, expectNoError, expectNotFixable, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciStaticLinksCheck';
const ROOT = `/sites/${SITE}/contents/static-internal-links`;
const CHECK = 'StaticInternalLinksCheck';
const CHECKS = [CHECK];

describe('StaticInternalLinksCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/StaticInternalLinksCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('is disabled by default', () => {
        expectCheckEnabled(CHECK, false);
    });

    it('detects HARDCODED_DOMAIN on a text which contains the domain of a site', () => {
        expectNotFixable(results, 'HARDCODED_DOMAIN', `${ROOT}/hardcoded-domain`);
        const error = results.errors.find(e => e.errorType === 'HARDCODED_DOMAIN' && e.nodePath === `${ROOT}/hardcoded-domain`);
        expectExtraInfo(error, 'domain', `${SITE}.content-integrity.test`);
        expectExtraInfo(error, 'property-name', 'text');
        expect(error.locale).to.equal('en');
    });

    it('does not report a relative link', () => {
        expectNoError(results, 'HARDCODED_DOMAIN', `${ROOT}/relative-link`);
    });
});
