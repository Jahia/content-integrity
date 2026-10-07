import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoError,
    fixAndVerify,
    resetCheckConfiguration,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciBinaryCheck';
const ROOT = `/sites/${SITE}/files/binary-properties`;
const CHECK = 'BinaryPropertiesSanityCheck';
const CHECKS = [CHECK];

describe('BinaryPropertiesSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/BinaryPropertiesSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
    });

    it('detects INVALID_BINARY on an empty file', () => {
        const error = expectError(results, 'INVALID_BINARY', `${ROOT}/empty.txt/jcr:content`);
        expectExtraInfo(error, 'property-name', 'jcr:data');
        expectExtraInfo(error, 'property-path', `${ROOT}/empty.txt/jcr:content/jcr:data`);
    });

    it('does not report a file with a binary', () => {
        expectNoError(results, 'INVALID_BINARY', `${ROOT}/valid.txt/jcr:content`);
    });

    it('accepts the empty files once configured to accept the zero byte binaries', () => {
        configureCheck(CHECK, 'accept-zero-byte-binaries', 'true');
        scan(ROOT, CHECKS).then(r => expectNoError(r, 'INVALID_BINARY', `${ROOT}/empty.txt/jcr:content`));
        resetCheckConfiguration(CHECK);
    });

    it('validates the binaries by reading their stream once configured', () => {
        configureCheck(CHECK, 'download-stream', 'true');
        scan(ROOT, CHECKS).then(r => {
            expectError(r, 'INVALID_BINARY', `${ROOT}/empty.txt/jcr:content`);
            expectNoError(r, 'INVALID_BINARY', `${ROOT}/valid.txt/jcr:content`);
        });
        resetCheckConfiguration(CHECK);
    });

    it('fixes INVALID_BINARY by deleting the file', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'INVALID_BINARY', `${ROOT}/empty.txt/jcr:content`);
    });
});
