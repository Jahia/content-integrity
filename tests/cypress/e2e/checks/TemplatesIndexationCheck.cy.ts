import {expectNoError, expectNotFixable, runFixture, scan, ScanResults} from '../../support/integrity';

// No site is needed: the templates are created in the module content-integrity. The suffix makes their names unique
const SUFFIX = 'ciTemplatesCheck';
const CHECKS = ['TemplatesIndexationCheck'];
const NOT_INDEXED = new RegExp(`^/modules/content-integrity/[^/]+/templates/ci-not-indexed-${SUFFIX}$`);
const INDEXED = new RegExp(`^/modules/content-integrity/[^/]+/templates/ci-indexed-${SUFFIX}$`);

describe('TemplatesIndexationCheck', () => {
    let results: ScanResults;

    before(() => {
        runFixture('checks/TemplatesIndexationCheck.groovy', {SITEKEY: SUFFIX});
        scan('/modules/content-integrity', CHECKS).then(r => {
            results = r;
        });
    });

    after(() => runFixture('checks/TemplatesIndexationCheck-cleanup.groovy', {SITEKEY: SUFFIX}));

    it('detects NOT_INDEXED_TEMPLATE on a template missing from the search index', () => {
        expectNotFixable(results, 'NOT_INDEXED_TEMPLATE', NOT_INDEXED);
    });

    it('does not report an indexed template', () => {
        expectNoError(results, 'NOT_INDEXED_TEMPLATE', INDEXED);
    });
});
