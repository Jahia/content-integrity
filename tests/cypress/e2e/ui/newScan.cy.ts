import {createTestSite, deleteTestSite, graphql, readExecution, resetCheckConfiguration, runFixture, scan} from '../../support/integrity';
import {clearFilters, getDialog, getDropdown, getMenuItem, getResultsTable, selectInDropdown, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiNewScan';
const ROOT = `/sites/${SITE}/contents/locks`;
const INCONSISTENT_LOCK = `${ROOT}/inconsistent-lock`;
const LOCKED_TRANSLATION = `${ROOT}/deletion-lock-on-translation/j:translation_en`;

const openNewScanDialog = (): void => {
    cy.contains('button', 'New scan').click();
    getDialog('New integrity scan').find('[id^="ci-check-"]').should('have.length.at.least', 26);
};

const readThreshold = (): Cypress.Chainable<string> =>
    graphql('{ integrity: contentIntegrity { check: integrityCheckById(id: "FlatStorageCheck") { configuration(name: "threshold") { value } } } }')
        .then(data => data.integrity.check.configuration.value);

/**
 * The scan request sent by the dialog. Apollo batches the queries, so a request carries one or several operations.
 */
const interceptRunScan = (): void => {
    cy.intercept('POST', '**/modules/graphql', req => {
        const operations = Array.isArray(req.body) ? req.body : [req.body];
        if (operations.some(o => o.operationName === 'ContentIntegrityRunScan')) {
            req.alias = 'runScan';
        }
    });
};

const runScanVariables = (body: unknown): Record<string, unknown> => {
    const operations = Array.isArray(body) ? body : [body];
    return operations.find(o => o.operationName === 'ContentIntegrityRunScan').variables;
};

describe('New scan', () => {
    let previousResultsId: string;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        // Other results, to switch between two scans
        scan(ROOT, ['LockSanityCheck']).then(results => {
            previousResultsId = results.resultsId;
        });
    });

    after(() => {
        resetCheckConfiguration('FlatStorageCheck');
        deleteTestSite(SITE);
    });

    beforeEach(() => visitAdmin());

    it('scans the whole repository, in all the workspaces, with the virtual nodes, by default', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').should('have.value', '/');
            cy.get('#ci-workspace-field [role=listbox]').should('contain.text', 'All workspaces');
            cy.get('#ci-virtual-nodes').should('be.checked');
            cy.get('ul[aria-label="Excluded paths"]').should('not.exist');
        });
    });

    it('lists the checks alphabetically, with the enabled ones selected', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('[id^="ci-check-"]').then(boxes => {
                const ids = boxes.toArray().map(b => b.id.replace('ci-check-', ''));
                expect(ids).to.deep.equal([...ids].sort((a, b) => a.localeCompare(b, 'en', {sensitivity: 'base'})));
            });
            ['LivePropertiesCheck', 'NodeNameInfoSanityCheck', 'StaticInternalLinksCheck', 'VersionHistoryCheck', 'VersionSanityCheck'].forEach(id => {
                cy.get(`#ci-check-${id}`).should('not.be.checked');
            });
            ['AceSanityCheck', 'LockSanityCheck', 'PropertyDefinitionsSanityCheck'].forEach(id => {
                cy.get(`#ci-check-${id}`).should('be.checked');
            });
            cy.contains('button', 'Cancel').click();
        });
        cy.get('[role=dialog]').should('not.exist');
    });

    it('selects all the checks, or none, and does not run a scan without any selected check', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('[id^="ci-check-"]').its('length').then(count => {
                cy.contains('button', 'Select all').click();
                cy.contains(`Integrity checks (${count} of ${count} selected)`).should('be.visible');
            });
            cy.contains('button', 'Unselect all').click();
            cy.contains('Integrity checks (0 of').should('be.visible');
            cy.contains('button', 'Run the scan').should('be.disabled');
            cy.contains('button', 'Cancel').click();
        });
    });

    it('links the documentation of the checks', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('a[aria-label="Documentation of AceSanityCheck"]')
                .should('have.attr', 'target', '_blank')
                .and('have.attr', 'href')
                .and('match', /^https?:\/\//);
        });
    });

    it('adds the excluded paths once each, and removes them', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-excluded-paths-field').contains('button', 'Add').as('add').should('be.disabled');
            cy.get('#ci-excluded-paths').type('/sites/a');
            cy.get('@add').should('not.be.disabled').click();
            cy.get('#ci-excluded-paths').should('have.value', '').type('/sites/b{enter}');
            // A path already excluded is not added twice
            cy.get('#ci-excluded-paths').type(' /sites/a {enter}');
            cy.get('ul[aria-label="Excluded paths"] li').should('have.length', 2);
            cy.get('button[aria-label="Remove the excluded path /sites/a"]').click();
            cy.get('ul[aria-label="Excluded paths"] li').should('have.length', 1).and('contain.text', '/sites/b');
            cy.get('button[aria-label="Remove the excluded path /sites/b"]').click();
            cy.get('ul[aria-label="Excluded paths"]').should('not.exist');
        });
    });

    it('runs a scan with the parameters of the dialog', () => {
        interceptRunScan();
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').clear().type(ROOT);
            cy.get('#ci-excluded-paths').type(`${INCONSISTENT_LOCK}{enter}`);
            cy.get('#ci-workspace-field [role=listbox]').click();
        });
        getMenuItem(/^default$/).click();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-virtual-nodes').click({force: true});
            cy.contains('button', 'Unselect all').click();
            cy.get('#ci-check-LockSanityCheck').click({force: true});
            cy.contains('Integrity checks (1 of').should('be.visible');
            cy.contains('button', 'Run the scan').click();
        });
        cy.wait('@runScan').then(({request}) => {
            expect(runScanVariables(request.body)).to.deep.include({
                path: ROOT,
                excludedPaths: [INCONSISTENT_LOCK],
                ws: 'EDIT',
                checks: ['LockSanityCheck'],
                skipMP: true
            });
        });
        // The lock errors do not block an XML import, so the default filter hides them
        cy.contains('Errors: 0 (total: 2)', {timeout: 60000}).should('be.visible');
        clearFilters();
        getResultsTable().within(() => {
            cy.contains(LOCKED_TRANSLATION).should('exist');
            cy.contains(INCONSISTENT_LOCK).should('not.exist');
        });
    });

    it('runs a scan, displays its results and its reports, then displays them again from the last scan', () => {
        openNewScanDialog();
        getDialog('New integrity scan').within(() => {
            cy.get('#ci-root-node').clear().type(ROOT);
            cy.contains('button', 'Unselect all').click();
            cy.get('#ci-check-LockSanityCheck').click({force: true});
            cy.contains('button', 'Run the scan').click();
        });
        cy.contains('Errors: 0 (total: 3)', {timeout: 60000}).should('be.visible');
        clearFilters();
        cy.contains('Errors: 3').should('be.visible');
        getResultsTable().within(() => {
            cy.contains(INCONSISTENT_LOCK).should('exist');
            cy.contains(LOCKED_TRANSLATION).should('exist');
        });
        cy.contains('Last scan').parents('section').first().within(() => {
            cy.contains('Finished').should('be.visible');
            // The results card displays this scan, so its card does not repeat the reports
            cy.contains('button', 'Show its results').should('not.exist');
        });

        readExecution().then(execution => {
            const resultsId = execution.resultsID;
            getDropdown('Scan').should('contain.text', resultsId);
            // The reports of the displayed results are offered next to the scan selector, and can be downloaded
            ['csv', 'xlsx'].forEach(extension => {
                cy.contains('a', extension.toUpperCase())
                    .should('have.attr', 'title', `${resultsId}-full.${extension}`)
                    .invoke('attr', 'href')
                    .then(href => cy.request(href as string).its('status').should('equal', 200));
            });

            selectInDropdown('Scan', previousResultsId);
            getDropdown('Scan').should('contain.text', previousResultsId);
            cy.contains('Last scan').parents('section').first().within(() => {
                cy.contains('a', `${resultsId}-full.csv`).should('be.visible');
                cy.contains('button', 'Show its results').click();
            });
            getDropdown('Scan').should('contain.text', resultsId);
            cy.contains('button', 'Show its results').should('not.exist');
        });
    });

    it('configures a check, then resets its configuration', () => {
        openNewScanDialog();
        cy.get('button[aria-label="Configure FlatStorageCheck"]').click();
        getDialog('Configure FlatStorageCheck').within(() => {
            cy.get('#ci-conf-threshold').should('have.value', '500').clear().type('abc');
            cy.contains('An integer value is expected').should('be.visible');
            cy.contains('button', 'Save').should('be.disabled');
            cy.get('#ci-conf-threshold').clear().type('42');
            cy.contains('button', 'Save').click();
        });
        cy.contains('[role=dialog]', 'Configure FlatStorageCheck').should('not.exist');
        readThreshold().should('equal', '42');

        cy.get('button[aria-label="Configure FlatStorageCheck"]').click();
        getDialog('Configure FlatStorageCheck').within(() => {
            cy.get('#ci-conf-threshold').should('have.value', '42');
            cy.contains('button', 'Reset to default values').click();
        });
        cy.contains('[role=dialog]', 'Configure FlatStorageCheck').should('not.exist');
        readThreshold().should('equal', '500');
    });
});
