import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixError, graphql, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciPagesCheck';
const HOME = `/sites/${SITE}/home`;
const MISSING_TEMPLATE = `${HOME}/missing-template`;
const CHECKS = ['PagesSanityCheck'];

type FixValues = { name: string; type: string; multiple: boolean; choices: string[]; choiceLabels: string[]; description: string };

const readFixValues = (resultsId: string, errorId: string): Cypress.Chainable<FixValues> =>
    graphql('query($r: String, $id: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $r) { error: errorById(id: $id) { fixable fixWithValues fixValues { name type multiple choices choiceLabels description } } } } }',
        {r: resultsId, id: errorId})
        .then(data => {
            const error = data.integrity.results.error;
            expect(error.fixable).to.be.true;
            expect(error.fixWithValues).to.be.true;
            return error.fixValues as FixValues;
        });

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
        expectExtraInfo(expectError(results, 'MISSING_TEMPLATE', MISSING_TEMPLATE), 'template-name', 'ci-template-which-does-not-exist');
    });

    it('does not report a page whose template exists', () => {
        expectNoError(results, 'MISSING_TEMPLATE', HOME);
    });

    describe('Fix', () => {
        beforeEach(() => runFixture('checks/PagesSanityCheck.groovy', {SITEKEY: SITE}));

        it('offers the templates available to the page, as the editors choose them', () => {
            scan(`/sites/${SITE}`, CHECKS).then(r => {
                const error = expectError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE);
                readFixValues(r.resultsId, error.id).then(fixValues => {
                    expect(fixValues.name).to.equal('j:templateName');
                    expect(fixValues.type).to.equal('String');
                    expect(fixValues.multiple).to.be.false;
                    // The template set of the test sites, templates-system, declares the page template home
                    expect(fixValues.choices).to.include('home');
                    expect(fixValues.choices).to.not.include('ci-template-which-does-not-exist');
                    expect(fixValues.choiceLabels).to.have.length(fixValues.choices.length);
                    expect(fixValues.description).to.contain('The template ci-template-which-does-not-exist does not exist');
                });
            });
        });

        it('does not fix MISSING_TEMPLATE without a template, which can not be guessed', () => {
            scan(`/sites/${SITE}`, CHECKS).then(r => {
                fixError(r.resultsId, expectError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE).id).its('fixed').should('be.false');
            });
        });

        it('refuses a template which is not available to the page', () => {
            scan(`/sites/${SITE}`, CHECKS).then(r => {
                fixError(r.resultsId, expectError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE).id, ['ci-another-missing-template']).then(result => {
                    expect(result.fixed).to.be.false;
                    expect(result.message).to.contain('The template ci-another-missing-template is not available');
                });
            });
            scan(`/sites/${SITE}`, CHECKS).then(r => expectError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE));
        });

        it('fixes MISSING_TEMPLATE with a template chosen among the available ones', () => {
            scan(`/sites/${SITE}`, CHECKS).then(r => {
                fixError(r.resultsId, expectError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE).id, ['home']).its('fixed').should('be.true');
            });
            scan(`/sites/${SITE}`, CHECKS).then(r => expectNoError(r, 'MISSING_TEMPLATE', MISSING_TEMPLATE));
            graphql(`{ jcr { nodeByPath(path: "${MISSING_TEMPLATE}") { property(name: "j:templateName") { value } } } }`)
                .its('jcr.nodeByPath.property.value').should('equal', 'home');
        });
    });
});
