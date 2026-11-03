import {createTestSite, deleteTestSite, expectExtraInfo, expectNoError, expectNotFixable, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciUnreadableStatusCheck';
const ROOT = `/sites/${SITE}/contents/unreadable-publication-status`;
const CHECKS = ['UnreadablePublicationStatusCheck'];

describe('UnreadablePublicationStatusCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/UnreadablePublicationStatusCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS, 'LIVE').then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('detects UNREADABLE_PUBLICATION_STATUS on a live translation in a language which is not active on the site', () => {
        expectNotFixable(results, 'UNREADABLE_PUBLICATION_STATUS', `${ROOT}/translated-in-inactive-language`);
        const error = results.errors.find(e => e.errorType === 'UNREADABLE_PUBLICATION_STATUS');
        expect(error.locale).to.equal('de');
        expectExtraInfo(error, 'locale', 'de');
    });

    it('does not report the translations in the active languages', () => {
        expectNoError(results, 'UNREADABLE_PUBLICATION_STATUS', `${ROOT}/translated-in-active-language`);
    });
});
