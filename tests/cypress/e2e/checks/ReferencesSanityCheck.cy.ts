import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectFixFails,
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

    it('does not check the back references by default', () => {
        expectNoError(results, 'INVALID_BACK_REF', `${ROOT}/back-referenced`);
    });

    it('detects INVALID_BACK_REF on a node referenced by an extension of a virtual node gone from its source', () => {
        configureCheck(CHECK, 'validate-back-refs', 'true');
        scan(`${ROOT}/back-referenced`, CHECKS).then(r => {
            const error = expectError(r, 'INVALID_BACK_REF', `${ROOT}/back-referenced`);
            expectExtraInfo(error, 'property-name', 'j:defaultCategory');
            expectExtraInfo(error, 'referencing-node-path', `/mounts/${SITE}-backref/linked.txt`);
        });
        resetCheckConfiguration(CHECK);
    });

    it('does not report INVALID_BACK_REF on a node referenced by a node which exists', () => {
        configureCheck(CHECK, 'validate-back-refs', 'true');
        scan(`${ROOT}/target`, CHECKS).then(r => expectNoError(r, 'INVALID_BACK_REF', `${ROOT}/target`));
        resetCheckConfiguration(CHECK);
    });

    it('provides no fix for INVALID_BACK_REF', () => {
        configureCheck(CHECK, 'validate-back-refs', 'true');
        expectNoFix(`${ROOT}/back-referenced`, CHECKS, 'EDIT', 'INVALID_BACK_REF', `${ROOT}/back-referenced`);
        resetCheckConfiguration(CHECK);
    });

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

    // The value is written by an editor: a placeholder in the text itself is not a link which can be removed, and its
    // many unclosed tags must not hold the fix
    it('does not report BROKEN_REF_IN_FIELD as fixed when the placeholder is not in a tag, and leaves the text unchanged', () => {
        const path = `${ROOT}/placeholder-in-text`;
        expectError(results, 'BROKEN_REF_IN_FIELD', path);
        expectFixFails(ROOT, CHECKS, 'EDIT', 'BROKEN_REF_IN_FIELD', path);
        readText(path).should('match', /^\S+ (<a ){2000}$/);
    });

    // A translator can write jcr:language on the translation node of their language: the error is reported, then fixed, in
    // the language of the translation node, as the platform resolves it from its name
    it('reports BROKEN_REF of a translation in the language of its node name, whatever its jcr:language', () => {
        expect(expectError(results, 'BROKEN_REF', `${ROOT}/i18n-reference`).locale).to.equal('fr');
    });

    it('fixes BROKEN_REF in the translation of the error only', () => {
        const path = `${ROOT}/i18n-reference`;
        const reference = (language: string): Cypress.Chainable<string> =>
            graphql('query($path: String!, $language: String!) { jcr { nodeByPath(path: $path) { property(name: "j:node", language: $language) { value } } } }',
                {path, language}).then(data => data.jcr.nodeByPath.property?.value ?? null);
        reference('en').then(english => {
            expect(english, 'English reference').to.not.be.null;
            fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'BROKEN_REF', path);
            reference('en').should('equal', english);
            reference('fr').should('be.null');
        });
    });
});
