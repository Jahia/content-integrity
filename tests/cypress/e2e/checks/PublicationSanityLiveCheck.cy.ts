import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectFixFails,
    expectNoError,
    fixAndVerify,
    resetCheckConfiguration,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciPublicationLiveCheck';
const ROOT = `/sites/${SITE}/contents/publication-live`;
const CHECK = 'PublicationSanityLiveCheck';
const CHECKS = [CHECK];
const DEEP_COMPARISON = [
    ['MISSING_PROP_LIVE', 'missing-property-live'],
    ['MISSING_PROP_DEFAULT', 'missing-property-default'],
    ['DIFFERENT_PROP_VAL', 'different-property-value'],
    ['DIFFERENT_MIXINS', 'different-mixins']
];

describe('PublicationSanityLiveCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/PublicationSanityLiveCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
    });

    describe('Detection', () => {
        let results: ScanResults;

        before(() => {
            scan(ROOT, CHECKS, 'LIVE').then(r => {
                results = r;
            });
        });

        it('detects NO_DEFAULT_NODE on a published node deleted from the default workspace only', () => {
            expectError(results, 'NO_DEFAULT_NODE', `${ROOT}/no-default-node`);
        });

        it('detects UNEXPECTED_UGC on a node flagged as UGC, which exists in default', () => {
            expectError(results, 'UNEXPECTED_UGC', `${ROOT}/unexpected-ugc`);
        });

        it('detects INCONSISTENT_UGC on a live node without j:originWS', () => {
            expectError(results, 'INCONSISTENT_UGC', `${ROOT}/inconsistent-ugc`);
        });

        it('does not compare the published nodes by default', () => {
            DEEP_COMPARISON.forEach(([errorType, name]) => expectNoError(results, errorType, `${ROOT}/${name}`));
        });
    });

    describe('Detection with the deep comparison of the published nodes', () => {
        let results: ScanResults;

        before(() => {
            configureCheck(CHECK, 'deep-compare-published-nodes', 'true');
            scan(ROOT, CHECKS, 'LIVE').then(r => {
                results = r;
            });
        });

        after(() => resetCheckConfiguration(CHECK));

        it('detects MISSING_PROP_LIVE on a property removed in live', () => {
            expectExtraInfo(expectError(results, 'MISSING_PROP_LIVE', `${ROOT}/missing-property-live`), 'property name', 'j:node');
        });

        it('detects MISSING_PROP_DEFAULT on a property added in live', () => {
            expectExtraInfo(expectError(results, 'MISSING_PROP_DEFAULT', `${ROOT}/missing-property-default`), 'property name', 'j:node');
        });

        it('detects DIFFERENT_PROP_VAL on a property changed in live', () => {
            expectExtraInfo(expectError(results, 'DIFFERENT_PROP_VAL', `${ROOT}/different-property-value`), 'property name', 'j:node');
        });

        it('detects DIFFERENT_MIXINS on a mixin added in live', () => {
            expectExtraInfo(expectError(results, 'DIFFERENT_MIXINS', `${ROOT}/different-mixins`), 'live-only-mixins', /jmix:tagged/);
        });

        DEEP_COMPARISON.forEach(([errorType, name]) => {
            it(`does not fix ${errorType}`, () => {
                expectFixFails(ROOT, CHECKS, 'LIVE', errorType, `${ROOT}/${name}`);
            });
        });
    });

    describe('Fix', () => {
        it('fixes NO_DEFAULT_NODE by publishing the live node to the default workspace', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'NO_DEFAULT_NODE', `${ROOT}/no-default-node`);
        });

        it('fixes UNEXPECTED_UGC', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'UNEXPECTED_UGC', `${ROOT}/unexpected-ugc`);
        });

        it('fixes INCONSISTENT_UGC', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'INCONSISTENT_UGC', `${ROOT}/inconsistent-ugc`);
        });
    });
});
