import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoFix,
    expectNoError,
    fixFromTwoScans,
    graphql,
    nodeExists,
    resetCheckConfiguration,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciReferencesCheck';
const ROOT = `/sites/${SITE}/contents/references`;
const CHECK = 'ReferencesSanityCheck';
const CHECKS = [CHECK];

// The English text of a node, as rendered: the stored placeholders of its links are resolved
const readText = (path: string): Cypress.Chainable<string> =>
    graphql('query($path: String!) { jcr { nodeByPath(path: $path) { property(name: "text", language: "en") { value } } } }', {path})
        .then(data => data.jcr.nodeByPath.property.value);

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

    it('detects BROKEN_REF_IN_FIELD on a link of a text without a jnt:referenceInField node', () => {
        const error = expectError(results, 'BROKEN_REF_IN_FIELD', `${ROOT}/link-without-reference-node`);
        expectExtraInfo(error, 'property-name', 'text');
        expectExtraInfo(error, 'placeholder', '##ref:link1##');
    });

    it('detects BROKEN_REF_IN_FIELD on a link of a text whose jnt:referenceInField node has no reference', () => {
        expectExtraInfo(expectError(results, 'BROKEN_REF_IN_FIELD', `${ROOT}/link-without-reference`), 'placeholder', '##ref:link1##');
    });

    it('does not report a valid link of a text', () => {
        expectNoError(results, 'BROKEN_REF_IN_FIELD', `${ROOT}/valid-link`);
    });

    it('does not check the references once configured not to', () => {
        configureCheck(CHECK, 'validate-refs', 'false');
        scan(ROOT, CHECKS).then(r => expectNoError(r, 'BROKEN_REF', `${ROOT}/broken-reference`));
        resetCheckConfiguration(CHECK);
    });

    // The back references are read with the system session of the scan, which can always read the referencing node
    it.skip('detects INVALID_BACK_REF (not reproducible)');

    it('provides no fix for BROKEN_REF_TO_VN', () => {
        expectNoFix(ROOT, CHECKS, 'EDIT', 'BROKEN_REF_TO_VN', `${ROOT}/broken-reference-to-virtual-node`);
    });

    it('fixes BROKEN_REF_IN_FIELD without a jnt:referenceInField node by removing the link from the text', () => {
        fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'BROKEN_REF_IN_FIELD', `${ROOT}/link-without-reference-node`);
        readText(`${ROOT}/link-without-reference-node`).should('equal', 'link');
    });

    it('fixes BROKEN_REF_IN_FIELD by removing the link from the text and its jnt:referenceInField node', () => {
        fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'BROKEN_REF_IN_FIELD', `${ROOT}/link-without-reference`);
        readText(`${ROOT}/link-without-reference`).should('equal', 'link');
        nodeExists(`${ROOT}/link-without-reference/j:referenceInField_text_en_1`).should('be.false');
    });

    it('keeps a valid link of a text when fixing', () => {
        readText(`${ROOT}/valid-link`).should('contain', '<a href=').and('contain', `${ROOT}/target.html`);
        nodeExists(`${ROOT}/valid-link/j:referenceInField_text_en_1`).should('be.true');
    });

    it('fixes BROKEN_REF by removing the broken value', () => {
        // Fixed from the first results, then from the second ones, where it is already fixed
        fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'BROKEN_REF', `${ROOT}/broken-reference`);
    });
});
