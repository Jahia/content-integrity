import {createTestSite, deleteTestSite, expectError, expectExtraInfo, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciWipCheck';
const ROOT = `/sites/${SITE}/contents/wip`;
const CHECKS = ['WipSanityCheck'];
const DEFAULT_ERRORS = [
    ['WIP_ON_TRANSLATION_NODE', `${ROOT}/on-translation/j:translation_en`],
    ['WIP_LEGACY_FORMAT', `${ROOT}/legacy-format`],
    ['WIP_UNEXPECTED_LANG', `${ROOT}/unexpected-language`],
    ['WIP_MISSING_PROP', `${ROOT}/missing-languages`],
    ['WIP_UNEXPECTED_PROP', `${ROOT}/unexpected-languages`],
    ['WIP_INCONSISTENT_STATUS_PROP', `${ROOT}/inconsistent-status`]
];

describe('WipSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/WipSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    describe('Detection', () => {
        DEFAULT_ERRORS.forEach(([errorType, path]) => {
            it(`detects ${errorType}`, () => {
                expectError(results, errorType, path);
            });
        });

        it('reports the language which is not a language of the site', () => {
            expectExtraInfo(expectError(results, 'WIP_UNEXPECTED_LANG', `${ROOT}/unexpected-language`), 'language', 'de');
        });

        it('reports the unknown status', () => {
            expectExtraInfo(expectError(results, 'WIP_INCONSISTENT_STATUS_PROP', `${ROOT}/inconsistent-status`), 'property-value', 'NOT_A_STATUS');
        });

        it('does not report a valid work in progress', () => {
            expect(results.errors.filter(e => e.nodePath === `${ROOT}/valid-wip`)).to.have.length(0);
        });

        it('detects WIP_IN_LIVE on a live node with a WIP status', () => {
            scan(ROOT, CHECKS, 'LIVE').then(r => expectError(r, 'WIP_IN_LIVE', `${ROOT}/in-live`));
        });
    });

    describe('Fix', () => {
        DEFAULT_ERRORS.forEach(([errorType, path]) => {
            it(`fixes ${errorType}`, () => {
                fixAndVerify(ROOT, CHECKS, 'EDIT', errorType, path);
            });
        });

        it('fixes WIP_IN_LIVE', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'WIP_IN_LIVE', `${ROOT}/in-live`);
        });
    });
});
