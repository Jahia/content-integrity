import {comparePaths, createTestSite, deleteTestSite, graphql, requestAs, rootUser, runFixture, scan, ScanResults} from '../../support/integrity';

// The errors of a node can block each other: a node is saved only once all its mandatory properties are set. The frame of
// the fixture misses its width and its height, so neither error is fixed alone, and both are fixed together.
const SITE = 'ciFixErrors';
const ROOT = `/sites/${SITE}/contents/node-errors`;
const FRAME = `${ROOT}/frame-without-size`;
const CHECKS = ['PropertyDefinitionsSanityCheck'];

const NODE_ERRORS = 'query($id: String, $filters: [String]!, $path: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id, filters: $filters) { ' +
    'errors: nodeErrors(workspace: "default", path: $path) { id errorType fixed extraInfos { label value } } } } }';
const FIX_ERRORS = 'mutation($id: String!, $ids: [String]!, $values: [[String]]) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { ' +
    'errors: fixErrors(ids: $ids, values: $values) { id fixed } } } }';

const propertyOf = (error: { extraInfos: { label: string; value: string }[] }) => error.extraInfos.find(i => i.label === 'property-name')?.value;

// The two errors of the frame, width first
const frameErrors = (results: ScanResults) => {
    const errors = results.errors.filter(e => e.nodePath === FRAME && e.errorType === 'EMPTY_MANDATORY_PROPERTY');
    expect(errors, 'errors of the frame').to.have.length(2);
    return [...errors].sort((a, b) => (propertyOf(a) === 'width' ? -1 : propertyOf(b) === 'width' ? 1 : 0));
};

describe('Fix of the errors of a node together', () => {
    before(() => createTestSite(SITE));

    beforeEach(() => runFixture('nodeErrors/frameWithoutSize.groovy', {SITEKEY: SITE}));

    after(() => deleteTestSite(SITE));

    it('lists the errors of a node, whatever the filters', () => {
        scan(ROOT, CHECKS).then(results => {
            graphql(NODE_ERRORS, {id: results.resultsId, filters: ['errorType;NO_SUCH_TYPE'], path: FRAME}).then(data => {
                const errors = data.integrity.results.errors;
                expect(errors.map((e: any) => e.errorType)).to.deep.equal(['EMPTY_MANDATORY_PROPERTY', 'EMPTY_MANDATORY_PROPERTY']);
                expect(errors.map(propertyOf).sort()).to.deep.equal(['height', 'width']);
            });
        });
    });

    it('does not fix one of two missing mandatory properties alone', () => {
        scan(ROOT, CHECKS).then(results => {
            const [width] = frameErrors(results);
            requestAs(rootUser(), 'mutation($id: String!, $e: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: fixError(id: $e, values: ["640"]) { fixed } } } }',
                {id: results.resultsId, e: width.id}).then(body => {
                expect(body.errors, 'GraphQL errors').to.have.length(1);
                expect(body.errors[0].message).to.contain('mandatory');
            });
        });
        scan(ROOT, CHECKS).then(results => frameErrors(results));
    });

    it('fixes the missing mandatory properties of a node together', () => {
        scan(ROOT, CHECKS).then(results => {
            const [width, height] = frameErrors(results);
            graphql(FIX_ERRORS, {id: results.resultsId, ids: [width.id, height.id], values: [['640'], ['480']]}).then(data => {
                expect(data.integrity.results.errors.map((e: any) => e.fixed)).to.deep.equal([true, true]);
            });
        });
        scan(ROOT, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === FRAME)).to.have.length(0));
        graphql(`{ jcr { nodeByPath(path: "${FRAME}") { width: property(name: "width") { value } height: property(name: "height") { value } } } }`)
            .its('jcr.nodeByPath').should('deep.equal', {width: {value: '640'}, height: {value: '480'}});
    });

    it('fixes none of the errors when a value is rejected', () => {
        scan(ROOT, CHECKS).then(results => {
            const [width, height] = frameErrors(results);
            requestAs(rootUser(), FIX_ERRORS, {id: results.resultsId, ids: [width.id, height.id], values: [['640'], ['not a number']]}).then(body => {
                expect(body.errors, 'GraphQL errors').to.have.length(1);
                expect(body.errors[0].message).to.contain('not a number');
            });
        });
        // Both properties are written before the node is saved: the valid one is not saved either
        scan(ROOT, CHECKS).then(results => frameErrors(results));
    });

    it('lists the errors of a node together, sorted by workspace then by path', () => {
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        scan(`/sites/${SITE}`, [...CHECKS, 'LockSanityCheck'], 'BOTH').then(results => {
            graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { errors(offset: 0, pageSize: 100) { workspace nodePath } } } }',
                {id: results.resultsId}).then(data => {
                const keys = data.integrity.results.errors.map((e: any) => [e.workspace, e.nodePath]);
                expect(keys.length, 'errors').to.be.greaterThan(2);
                const sorted = [...keys].sort((a, b) => (a[0] === b[0] ? comparePaths(a[1], b[1]) : (a[0] < b[0] ? -1 : 1)));
                expect(keys).to.deep.equal(sorted);
            });
        });
    });

    it('filters the errors whose fix has failed, and stores the failure with the results', () => {
        const fixStatus = (id: string) => graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { ' +
            'possibleValues(names: ["fixStatus"], withErrorsOnly: true) { values { name count } } } } }', {id})
            .then(data => Object.fromEntries(data.integrity.results.possibleValues[0].values.map((v: any) => [v.name, v.count])));
        const storedFailures = (id: string) => graphql('query($q: String!) { jcr { nodesByQuery(query: $q) { nodes { property(name: "integrity:failedErrors") { values } } } } }',
            {q: `select * from [integrity:scanReport] where [integrity:resultsId] = '${id}'`})
            .then(data => data.jcr.nodesByQuery.nodes[0].property?.values || []);
        scan(ROOT, CHECKS).then(results => {
            const [width, height] = frameErrors(results);
            // No fix has run yet: the errors are skipped
            fixStatus(results.resultsId).should('deep.equal', {skipped: 2});
            // Without a value, the fix of a missing width runs, and can't guess the value
            graphql('mutation($id: String!, $ids: [String]!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { errors: fixErrors(ids: $ids) { fixed fixFailed } } } }',
                {id: results.resultsId, ids: [width.id]}).its('integrity.results.errors').should('deep.equal', [{fixed: false, fixFailed: true}]);
            fixStatus(results.resultsId).should('deep.equal', {skipped: 1, notFixed: 1});
            graphql('query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id, filters: ["fixStatus;notFixed"]) { errors(offset: 0, pageSize: 10) { id } } } }',
                {id: results.resultsId}).its('integrity.results.errors').should('deep.equal', [{id: width.id}]);
            storedFailures(results.resultsId).should('deep.equal', [width.id]);
            // Fixed since, the error is not failed anymore
            graphql(FIX_ERRORS, {id: results.resultsId, ids: [width.id, height.id], values: [['640'], ['480']]});
            fixStatus(results.resultsId).should('deep.equal', {fixed: 2});
            storedFailures(results.resultsId).should('deep.equal', []);
        });
    });
});
