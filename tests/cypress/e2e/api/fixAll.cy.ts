import {createTestSite, deleteTestSite, getErrors, graphql, runFixture, scan} from '../../support/integrity';

const SITE = 'ciApiFixAll';
const CHECKS = ['JCRLanguagePropertyCheck', 'LockSanityCheck', 'PagesSanityCheck', 'PropertyDefinitionsSanityCheck'];
const MISSING_MANDATORY = `/sites/${SITE}/contents/property-definitions/missing-mandatory`;

type FixAllResult = { fixed: number; failed: number; failedIds: string[]; skipped: number; alreadyFixed: number };

const fixAll = (resultsId: string, filters: string[]): Cypress.Chainable<FixAllResult> =>
    graphql('query($id: String, $filters: [String]) { integrity: contentIntegrity { results: scanResultsDetails(id: $id, filters: $filters) { fixAll: fixAllErrors { fixed failed failedIds skipped alreadyFixed } } } }',
        {id: resultsId, filters}).then(data => data.integrity.results.fixAll as FixAllResult);

/**
 * The language errors are fixed by their check. The missing template has no fix. The missing mandatory property is
 * fixed with a value typed by an administrator. The lock errors are fixed by their check, but do not block an XML import.
 */
describe('Fix of all the errors matching filters', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/JCRLanguagePropertyCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE});
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('fixes only the errors matching the filters, skips the unfixable ones, then counts them as already fixed', () => {
        scan(`/sites/${SITE}`, CHECKS).then(results => {
            const matching = results.errors.filter(e => ['JCRLanguagePropertyCheck', 'PagesSanityCheck'].includes(e.checkName));
            expect(matching.map(e => e.errorType)).to.have.members(['MISSING_JCR_LANGUAGE_PROP', 'INCONSISTENT_JCR_LANGUAGE_PROP', 'MISSING_TEMPLATE']);

            fixAll(results.resultsId, ['importError;true', 'checkName;JCRLanguagePropertyCheck']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 2, failed: 0, failedIds: [], skipped: 0, alreadyFixed: 0});
            });
            fixAll(results.resultsId, ['checkName;PagesSanityCheck']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 0, failed: 0, failedIds: [], skipped: 1, alreadyFixed: 0});
            });
            // The fixed status is stored with the results
            fixAll(results.resultsId, ['checkName;JCRLanguagePropertyCheck']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 0, failed: 0, failedIds: [], skipped: 0, alreadyFixed: 2});
            });
        });

        scan(`/sites/${SITE}`, CHECKS).then(results => {
            const types = results.errors.map(e => e.errorType);
            expect(types).to.not.include('MISSING_JCR_LANGUAGE_PROP');
            expect(types).to.not.include('INCONSISTENT_JCR_LANGUAGE_PROP');
            expect(types).to.include('MISSING_TEMPLATE');
            expect(types).to.include('INCONSISTENT_LOCK');
        });
    });

    it('skips the errors fixed with a typed value', () => {
        scan(`/sites/${SITE}`, CHECKS).then(results => {
            const error = results.errors.find(e => e.errorType === 'EMPTY_MANDATORY_PROPERTY' && e.nodePath === MISSING_MANDATORY);
            expect(error.fixWithValues).to.be.true;
            fixAll(results.resultsId, ['errorType;EMPTY_MANDATORY_PROPERTY']).then(outcome => {
                expect(outcome.fixed).to.equal(0);
                expect(outcome.skipped).to.be.greaterThan(0);
            });
        });
        scan(`/sites/${SITE}`, CHECKS).then(results => {
            expect(results.errors.filter(e => e.errorType === 'EMPTY_MANDATORY_PROPERTY' && e.nodePath === MISSING_MANDATORY)).to.have.length(1);
        });
    });

    it('skips the errors without fix, and the ones fixed with values', () => {
        scan(`/sites/${SITE}/contents/property-definitions`, ['PropertyDefinitionsSanityCheck']).then(results => {
            // The right value of a value of the wrong type can't be guessed: its check provides no fix for it
            const noFix = results.errors.filter(e => e.errorType === 'INVALID_VALUE_TYPE');
            // A value which breaks the constraints is fixed with a chosen value
            const chosen = results.errors.filter(e => e.errorType === 'INVALID_VALUE_CONSTRAINT');
            expect(noFix).to.have.length(1);
            expect(chosen).to.have.length(2);
            expect(noFix[0].fixable).to.be.false;
            fixAll(results.resultsId, ['errorType;INVALID_VALUE_TYPE']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 0, failed: 0, failedIds: [], skipped: 1, alreadyFixed: 0});
            });
            fixAll(results.resultsId, ['errorType;INVALID_VALUE_CONSTRAINT']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 0, failed: 0, failedIds: [], skipped: chosen.length, alreadyFixed: 0});
            });
        });
    });

    it('returns the identifiers of the errors whose fix has failed', () => {
        // A site without page: its check flags a page as home, and finds none
        runFixture('checks/HomePageDeclarationCheck.groovy', {SITEKEY: SITE, SCENARIO: 'NO_PAGE'});
        scan(`/sites/${SITE}`, ['HomePageDeclarationCheck']).then(results => {
            const failing = results.errors.filter(e => e.errorType === 'NO_HOME');
            expect(failing).to.have.length(1);
            expect(failing[0].fixable).to.be.true;
            fixAll(results.resultsId, ['errorType;NO_HOME']).then(outcome => {
                expect(outcome).to.deep.equal({fixed: 0, failed: 1, failedIds: failing.map(e => e.id), skipped: 0, alreadyFixed: 0});
            });
        });
        runFixture('checks/HomePageDeclarationCheck.groovy', {SITEKEY: SITE, SCENARIO: 'RESTORE'});
    });

    it('skips the errors whose node has been removed since the scan', () => {
        scan(`/sites/${SITE}/contents/property-definitions`, ['PropertyDefinitionsSanityCheck']).then(results => {
            const removed = results.errors.filter(e => e.nodePath.endsWith('/missing-mandatory') || e.nodePath.includes('/invalid-'));
            expect(removed).to.have.length.greaterThan(0);
            runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
            // Without its node, an error is not fixable: an error fixed with a value is not fixed without it
            getErrors(results.resultsId).then(errors => {
                errors.filter(e => removed.some(r => r.id === e.id)).forEach(e => expect(e.fixable, `${e.errorType} on ${e.nodePath}`).to.be.false);
            });
            fixAll(results.resultsId, []).then(outcome => {
                expect(outcome.failed).to.equal(0);
                expect(outcome.skipped).to.equal(results.errors.length);
            });
        });
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
    });

    it('fixes all the errors of the results without filter', () => {
        scan(`/sites/${SITE}/contents/locks`, ['LockSanityCheck']).then(results => {
            expect(results.errors).to.have.length.greaterThan(0);
            fixAll(results.resultsId, []).then(outcome => {
                expect(outcome).to.deep.equal({fixed: results.errors.length, failed: 0, failedIds: [], skipped: 0, alreadyFixed: 0});
            });
        });
        scan(`/sites/${SITE}/contents/locks`, ['LockSanityCheck']).then(results => expect(results.errors).to.have.length(0));
    });

    it('returns no results for an unknown ID', () => {
        graphql('{ integrity: contentIntegrity { results: scanResultsDetails(id: "default_unknown") { fixAll: fixAllErrors { fixed } } } }')
            .then(data => expect(data.integrity.results).to.be.null);
    });
});
