import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, expectNotFixable, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciPagesCheck';
const HOME = `/sites/${SITE}/home`;
const CHECKS = ['PagesSanityCheck'];

describe('PagesSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
        scan(`/sites/${SITE}`, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('detects MISSING_TEMPLATE on a page whose template does not exist', () => {
        expectExtraInfo(expectError(results, 'MISSING_TEMPLATE', `${HOME}/missing-template`), 'template-name', 'ci-template-which-does-not-exist');
    });

    it('does not report a page whose template exists', () => {
        expectNoError(results, 'MISSING_TEMPLATE', HOME);
    });

    it('provides no fix for MISSING_TEMPLATE', () => {
        expectNotFixable(results, 'MISSING_TEMPLATE', `${HOME}/missing-template`);
    });
});
