import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectFixFails, fixAndVerify, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciPublicationDefaultCheck';
const ROOT = `/sites/${SITE}/contents/publication-default`;
const CHECKS = ['PublicationSanityDefaultCheck'];

describe('PublicationSanityDefaultCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/PublicationSanityDefaultCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    describe('Detection', () => {
        it('detects NO_LIVE_NODE on a node flagged as published, which has never been published', () => {
            expectError(results, 'NO_LIVE_NODE', `${ROOT}/flagged-as-published`);
        });

        it('detects DIFFERENT_PATH on a published node moved in live', () => {
            const error = expectError(results, 'DIFFERENT_PATH', `${ROOT}/moved-in-live`);
            expectExtraInfo(error, 'live-node-path', `${ROOT}/live-destination/moved-in-live`);
        });

        it('detects DIFFERENT_PATH_POTENTIAL_FP when the moved node is the root of the scan', () => {
            scan(`${ROOT}/moved-in-live`, CHECKS).then(r => expectError(r, 'DIFFERENT_PATH_POTENTIAL_FP', `${ROOT}/moved-in-live`));
        });

        it('detects PATH_CONFLICT on a node whose live path is used by another node', () => {
            expectExtraInfo(expectError(results, 'PATH_CONFLICT', `${ROOT}/path-conflict`), 'conflicted-edit-node', 'none');
        });

        it('detects DIFFERENT_PT on a published node whose live node has another primary type', () => {
            expectExtraInfo(expectError(results, 'DIFFERENT_PT', `${ROOT}/different-primary-type`), 'live-node-primary-type', 'jnt:bigText');
        });
    });

    describe('Fix', () => {
        ['DIFFERENT_PATH:moved-in-live', 'PATH_CONFLICT:path-conflict', 'DIFFERENT_PT:different-primary-type'].forEach(entry => {
            const [errorType, name] = entry.split(':');
            it(`does not fix ${errorType}`, () => {
                expectFixFails(ROOT, CHECKS, 'EDIT', errorType, `${ROOT}/${name}`);
            });
        });

        it('fixes NO_LIVE_NODE by removing the publication flag', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'NO_LIVE_NODE', `${ROOT}/flagged-as-published`);
        });
    });
});
