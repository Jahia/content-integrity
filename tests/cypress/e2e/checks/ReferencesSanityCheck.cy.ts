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

const SITE = 'ciReferencesCheck';
const ROOT = `/sites/${SITE}/contents/references`;
const CHECK = 'ReferencesSanityCheck';
const CHECKS = [CHECK];

describe('ReferencesSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/ReferencesSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        runFixture('checks/ReferencesSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('detects BROKEN_REF on a reference to a node which does not exist', () => {
        const error = expectError(results, 'BROKEN_REF', `${ROOT}/broken-reference`);
        expectExtraInfo(error, 'property-name', 'j:node');
        expectExtraInfo(error, 'missing-uuid', '0c1e57ed-0000-4000-8000-000000000000');
    });

    it('detects BROKEN_REF_TO_VN on a reference to a virtual node which can not be resolved', () => {
        expectExtraInfo(expectError(results, 'BROKEN_REF_TO_VN', `${ROOT}/broken-reference-to-virtual-node`), 'missing-uuid', '0c1e57ed-0000-4000-9000-000000000000');
    });

    it('does not report a valid reference', () => {
        expectNoError(results, 'BROKEN_REF', `${ROOT}/valid-reference`);
    });

    it('does not check the references once configured not to', () => {
        configureCheck(CHECK, 'validate-refs', 'false');
        scan(ROOT, CHECKS).then(r => expectNoError(r, 'BROKEN_REF', `${ROOT}/broken-reference`));
        resetCheckConfiguration(CHECK);
    });

    // The back references are read with the system session of the scan, which can always read the referencing node
    it.skip('detects INVALID_BACK_REF (not reproducible)');

    it('does not fix BROKEN_REF_TO_VN', () => {
        expectFixFails(ROOT, CHECKS, 'EDIT', 'BROKEN_REF_TO_VN', `${ROOT}/broken-reference-to-virtual-node`);
    });

    it('fixes BROKEN_REF by removing the broken value', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'BROKEN_REF', `${ROOT}/broken-reference`);
    });
});
