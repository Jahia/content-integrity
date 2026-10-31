import {clearFilters, getDropdown, getResultsTable, getScanLabel, openDropdown, openErrorDetails, selectInDropdown, visitAdmin} from '../cypress/support/adminPage';
import {getErrors, graphql, IntegrityError} from '../cypress/support/integrity';

/**
 * The screenshots of the documentation of the checks, taken from the scans of the demo, with the details of the first
 * displayed error open:
 * - docs/img/checks/<check>.png: the results of the scan with the most error types of the check, filtered on it
 * - docs/img/checks/<check>/<error type>.png: the results of the scan with the most errors of the type, filtered on the
 *   check and the type, for each error type found in the scans of the demo
 * Run by screenshots.sh, once generate.sh has run.
 *
 * Optional Cypress env variables, as comma-separated lists, to take only some screenshots: RESULTS, the ids of the scans
 * to take them from (by default, all the stored scans), CHECKS, the checks, ERROR_TYPES, the error types. With
 * ERROR_TYPES, the screenshots of the checks are not taken.
 */

type Rank = { id: string; count: number; types: number };

const toList = (value: unknown): string[] => String(value || '').split(',').map(v => v.trim()).filter(Boolean);
// The lists of the env, read with cy.env() in the before hook
let selectedResults: string[] = [];
let selectedChecks: string[] = [];
let selectedErrorTypes: string[] = [];

/**
 * Selects an item of a filter of the results table, found by its text. The menu can be longer than the screen: the item
 * is clicked even when the overlay of the menu covers it.
 */
const filterOn = (label: string, value: string): void => {
    openDropdown(label);
    cy.contains('li.moonstone-menuItem', new RegExp(`^${value} \\(`)).click({force: true});
    cy.get('.moonstone-menu_overlay').should('not.exist');
    getDropdown(label).should('contain.text', value);
};

/**
 * Displays the errors of the check, and of the error type if given, in the results, opens the details of the first one,
 * and takes the screenshot. The table is read once it counts the expected errors, so once the filters apply.
 * The default filter on the errors which block an XML import, the only one to clear, is set only on the results which
 * have such errors.
 */
const capture = (resultsId: string, check: string, errorType: string | undefined, count: number, name: string): void => {
    visitAdmin();
    getScanLabel(resultsId).then(label => selectInDropdown('Scan', label));
    graphql('{ integrity: contentIntegrity { scanResults: scanResultsSummaries { id importErrorCount } } }')
        .then(data => {
            const summary = data.integrity.scanResults.find((r: { id: string }) => r.id === resultsId);
            if (summary?.importErrorCount > 0) clearFilters();
        });
    filterOn('Check name', check);
    if (errorType) filterOn('Error type', errorType);
    cy.contains(new RegExp(`^Errors: ${count} \\(total: \\d+\\)$`)).should('be.visible');
    getResultsTable().find('tr[data-error-id]').first().invoke('attr', 'data-node-path').then(path => openErrorDetails(path as string));
    cy.screenshot(name, {capture: 'viewport', overwrite: true});
};

describe('Screenshots of the checks', () => {
    const errorsByResults: Record<string, IntegrityError[]> = {};
    let checks: string[] = [];

    // The scans of the demo, the one which shows the most error types first, then the one with the most errors
    const rank = (matches: (e: IntegrityError) => boolean): Rank[] => Object.entries(errorsByResults)
        .map(([id, errors]) => {
            const matching = errors.filter(matches);
            return {id, count: matching.length, types: new Set(matching.map(e => e.errorType)).size};
        })
        .filter(r => r.count > 0)
        .sort((a, b) => b.types - a.types || b.count - a.count);

    before(() => {
        cy.env(['RESULTS', 'CHECKS', 'ERROR_TYPES']).then(env => {
            selectedResults = toList(env.RESULTS);
            selectedChecks = toList(env.CHECKS);
            selectedErrorTypes = toList(env.ERROR_TYPES);
        });
        graphql('{ integrity: contentIntegrity { checks: integrityChecks { id } scanResults: scanResultsSummaries { id } } }').then(data => {
            checks = data.integrity.checks.map((c: { id: string }) => c.id).filter((id: string) => selectedChecks.length === 0 || selectedChecks.includes(id)).sort();
            data.integrity.scanResults.filter((r: { id: string }) => selectedResults.length === 0 || selectedResults.includes(r.id)).forEach((r: { id: string }) => getErrors(r.id).then(errors => {
                errorsByResults[r.id] = errors;
            }));
        });
    });

    it('captures the errors of every check', () => {
        cy.wrap(null).then(() => {
            if (selectedErrorTypes.length > 0) return;
            checks.forEach(check => {
                const ranked = rank(e => e.checkName === check);
                if (ranked.length === 0) {
                    cy.log(`No error of ${check} in the scans of the demo: no screenshot`);
                    return;
                }

                capture(ranked[0].id, check, undefined, ranked[0].count, check);
            });
        });
    });

    it('captures every error type of every check', () => {
        cy.wrap(null).then(() => {
            checks.forEach(check => {
                const types = [...new Set(Object.values(errorsByResults).flat().filter(e => e.checkName === check).map(e => e.errorType))]
                    .filter(errorType => selectedErrorTypes.length === 0 || selectedErrorTypes.includes(errorType)).sort();
                types.forEach(errorType => {
                    const ranked = rank(e => e.checkName === check && e.errorType === errorType);
                    capture(ranked[0].id, check, errorType, ranked[0].count, `${check}/${errorType}`);
                });
            });
        });
    });
});
