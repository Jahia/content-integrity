import {getDetailsPanel, getDialog, getDropdown, getResultsTable, getScanLabel, openDropdown, openErrorDetails, selectInDropdown, visitAdmin} from '../cypress/support/adminPage';
import {graphql} from '../cypress/support/integrity';

/**
 * The screenshots of the administration page in the README, docs/img/ui-<name>.png, taken from the results of one scan of
 * the demo: the last id of the Cypress env variable RESULTS, by default the latest stored scan which holds the errors of
 * the node displayed in the details, the one of the demo site once generate.sh has run, whatever was scanned after it.
 * Run by screenshots.sh.
 */

// The node of the demo whose errors are displayed in the details: a value to choose among the accepted ones
const DETAILS_PATH = '/sites/integrityDemo/contents/property-definitions/invalid-choices';
const DETAILS_CHECK = 'PropertyDefinitionsSanityCheck';

let resultsId: string;

const openResults = (): void => {
    visitAdmin();
    getScanLabel(resultsId).then(label => selectInDropdown('Scan', label));
    getResultsTable().find('tr[data-error-id]').should('have.length.greaterThan', 0);
};

describe('Screenshots of the administration page', () => {
    before(() => {
        cy.env(['RESULTS']).then(env => {
            const selected = String(env.RESULTS || '').split(',').map(v => v.trim()).filter(Boolean);
            if (selected.length > 0) {
                resultsId = selected[selected.length - 1];
                return;
            }

            graphql('{ integrity: contentIntegrity { scanResults: scanResultsSummaries { id status } } }').then(data => {
                const finished: string[] = data.integrity.scanResults.filter((s: { status: string }) => s.status === 'finished').map((s: { id: string }) => s.id);
                finished.forEach(id => graphql('query($id: String, $path: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { nodeErrors(workspace: "default", path: $path) { id } } } }',
                    {id, path: DETAILS_PATH}).then(details => {
                    if (details.integrity.results.nodeErrors.length > 0) resultsId = id;
                }));
            });
            cy.wrap(null).then(() => expect(resultsId, `Results of a finished scan with the errors of ${DETAILS_PATH}`).to.exist);
        });
    });

    it('captures the results of a scan', () => {
        openResults();
        cy.screenshot('ui-overview', {capture: 'viewport', overwrite: true});
    });

    it('captures the errors of a node', () => {
        openResults();
        openDropdown('Check name');
        cy.contains('li.moonstone-menuItem', new RegExp(`^${DETAILS_CHECK} \\(`)).click({force: true});
        getDropdown('Check name').should('contain.text', DETAILS_CHECK);
        openErrorDetails(DETAILS_PATH);
        getDetailsPanel().should('contain.text', 'Fix with a value');
        cy.screenshot('ui-error-details', {capture: 'viewport', overwrite: true});
    });

    it('captures the dialog of a new scan', () => {
        openResults();
        cy.contains('button', 'New scan').click();
        getDialog('New integrity scan').should('contain.text', 'Integrity checks');
        cy.screenshot('ui-new-scan', {capture: 'viewport', overwrite: true});
    });
});
