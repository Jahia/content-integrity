import {getErrors, graphql, runFixture, scan} from '../../support/integrity';

const MOUNT = 'ciApiVirtualNodes';
const EMPTY_FILE = `/mounts/${MOUNT}/empty.txt/jcr:content`;

const FIX_ERROR = 'query($id: String, $e: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: fixError(id: $e) { fixed fixable virtualNode } } } }';
const FIX_ALL = 'query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { fixAll: fixAllErrors { fixed skipped failed } } } }';

/**
 * A fix of an error of a virtual node would change its external source: BinaryPropertiesSanityCheck would delete the
 * empty file of the mounted folder.
 */
describe('Errors of virtual nodes', () => {
    before(() => runFixture('virtualNodes/vfsMount.groovy', {MOUNT_NAME: MOUNT}));

    after(() => runFixture('virtualNodes/vfsMount-cleanup.groovy', {MOUNT_NAME: MOUNT}));

    it('reports the error of a virtual node, as not fixable', () => {
        scan(`/mounts/${MOUNT}`, ['BinaryPropertiesSanityCheck']).then(results => {
            const error = results.errors.find(e => e.nodePath === EMPTY_FILE);
            expect(error, 'The error of the empty file').to.exist;
            expect(error.fixable).to.equal(false);
            graphql('query($id: String, $e: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { error: errorById(id: $e) { virtualNode fixable } } } }',
                {id: results.resultsId, e: error.id}).its('integrity.results.error').should('deep.equal', {virtualNode: true, fixable: false});
        });
    });

    it('does not fix the error of a virtual node, nor change its source', () => {
        scan(`/mounts/${MOUNT}`, ['BinaryPropertiesSanityCheck']).then(results => {
            const error = results.errors.find(e => e.nodePath === EMPTY_FILE);
            graphql(FIX_ERROR, {id: results.resultsId, e: error.id}).its('integrity.results.error.fixed').should('equal', false);
            graphql(FIX_ALL, {id: results.resultsId}).its('integrity.results.fixAll').should('deep.equal', {fixed: 0, skipped: 1, failed: 0});
            getErrors(results.resultsId as string).then(errors => expect(errors.find(e => e.id === error.id).fixed).to.equal(false));
            // The file is still in the mounted folder
            scan(`/mounts/${MOUNT}`, ['BinaryPropertiesSanityCheck']).its('errors').then(errors => {
                expect((errors as { nodePath: string }[]).map(e => e.nodePath)).to.include(EMPTY_FILE);
            });
        });
    });
});
