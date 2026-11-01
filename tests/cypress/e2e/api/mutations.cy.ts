import {requestAs, rootUser} from '../../support/integrity';

// The fields which change the server: they are mutations, and none of them is a field of the queries
const QUERY_FIELDS_WHICH_WRITE = [
    '{ contentIntegrity { deleteScanResults(id: "unknown") } }',
    '{ contentIntegrity { deleteAllScanResults } }',
    '{ contentIntegrity { integrityScan { scan(workspace: EDIT, checksToRun: ["LockSanityCheck"]) } } }',
    '{ contentIntegrity { integrityScan(id: "unknown") { stopRunningScan } } }',
    '{ contentIntegrity { integrityCheckById(id: "FlatStorageCheck") { configure(name: "threshold", value: "3") } } }',
    '{ contentIntegrity { integrityCheckById(id: "FlatStorageCheck") { resetConfiguration(name: "threshold") } } }',
    '{ contentIntegrity { integrityCheckById(id: "FlatStorageCheck") { resetAllConfigurations } } }',
    '{ contentIntegrity { scanResultsDetails { fixError(id: "unknown") { id } } } }',
    '{ contentIntegrity { scanResultsDetails { fixErrors(ids: ["unknown"]) { id } } } }',
    '{ contentIntegrity { scanResultsDetails { fixAllErrors { fixed } } } }'
];

const RESET_FLAT_STORAGE = 'mutation { contentIntegrity { integrityCheckById(id: "FlatStorageCheck") { resetAllConfigurations } } }';

describe('Mutations', () => {
    it('offers no field which changes the server in the queries', () => {
        QUERY_FIELDS_WHICH_WRITE.forEach(query => {
            requestAs(rootUser(), query).then(body => {
                expect(body.data, query).to.be.null;
                expect(body.errors?.[0]?.extensions?.classification, query).to.equal('ValidationError');
            });
        });
    });

    it('runs a mutation sent with a POST', () => {
        requestAs(rootUser(), RESET_FLAT_STORAGE).then(body => {
            expect(body.errors).to.be.undefined;
            expect(body.data.contentIntegrity.integrityCheckById.resetAllConfigurations).to.be.true;
        });
    });

    it('refuses a mutation sent with a GET', () => {
        cy.request({
            method: 'GET',
            url: '/modules/graphql',
            qs: {query: RESET_FLAT_STORAGE},
            auth: rootUser(),
            headers: {Referer: `${Cypress.config().baseUrl}/`},
            failOnStatusCode: false
        }).its('body').then(body => {
            expect(body.data).to.be.null;
            expect(body.errors?.[0]?.extensions?.classification).to.equal('OperationNotSupported');
        });
    });

    it('fixes the errors of the results given by their identifier only', () => {
        requestAs(rootUser(), 'mutation { contentIntegrity { scanResultsDetails { fixAllErrors { fixed } } } }').then(body => {
            expect(body.data).to.be.null;
            expect(body.errors?.[0]?.extensions?.classification).to.equal('ValidationError');
        });
    });

    it('finds no results for a blank identifier, rather than the latest ones', () => {
        requestAs(rootUser(), 'mutation { contentIntegrity { scanResultsDetails(id: " ") { fixAllErrors { fixed } } } }').then(body => {
            expect(body.errors).to.be.undefined;
            expect(body.data.contentIntegrity.scanResultsDetails).to.be.null;
        });
    });
});
