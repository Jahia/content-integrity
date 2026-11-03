import {createTestSite, deleteTestSite, expectError, expectExtraInfo, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciJcrLanguageCheck';
const ROOT = `/sites/${SITE}/contents/jcr-language`;
const CHECKS = ['JCRLanguagePropertyCheck'];
const MISSING = `${ROOT}/missing-language/j:translation_en`;
const INCONSISTENT = `${ROOT}/inconsistent-language/j:translation_en`;

describe('JCRLanguagePropertyCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/JCRLanguagePropertyCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('detects MISSING_JCR_LANGUAGE_PROP on a translation node without jcr:language', () => {
        expect(expectError(results, 'MISSING_JCR_LANGUAGE_PROP', MISSING).locale).to.equal('en');
    });

    it('detects INCONSISTENT_JCR_LANGUAGE_PROP on a translation node whose jcr:language does not match its name', () => {
        expectExtraInfo(expectError(results, 'INCONSISTENT_JCR_LANGUAGE_PROP', INCONSISTENT), 'jcr-language-prop-value', 'fr');
    });

    it('fixes MISSING_JCR_LANGUAGE_PROP', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'MISSING_JCR_LANGUAGE_PROP', MISSING);
    });

    it('fixes INCONSISTENT_JCR_LANGUAGE_PROP', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'INCONSISTENT_JCR_LANGUAGE_PROP', INCONSISTENT);
    });
});
