import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixAndVerify, fixError, graphql, runFixture, scan} from '../../support/integrity';

const SITE = 'ciUndeployedModulesCheck';
const SITE_PATH = `/sites/${SITE}`;
const CHECKS = ['UndeployedModulesReferencesCheck'];

describe('UndeployedModulesReferencesCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/UndeployedModulesReferencesCheck.groovy', {SITEKEY: SITE});
    });

    after(() => deleteTestSite(SITE));

    it('detects UNDEPLOYED_MODULE_ON_SITE on a site which references a module not deployed', () => {
        scan(SITE_PATH, CHECKS).then(results => {
            expectExtraInfo(expectError(results, 'UNDEPLOYED_MODULE_ON_SITE', SITE_PATH), 'module', 'ci-undeployed-module');
            expect(results.errors.filter(e => e.errorType === 'UNDEPLOYED_MODULE_ON_SITE'), 'Only the undeployed module is reported').to.have.length(1);
        });
    });

    it('fixes UNDEPLOYED_MODULE_ON_SITE by removing the module from the site', () => {
        fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'UNDEPLOYED_MODULE_ON_SITE', SITE_PATH);
    });
});

describe('UndeployedModulesReferencesCheck on a module installed but not started', () => {
    const STOPPED_SITE = 'ciStoppedModuleCheck';
    const STOPPED_SITE_PATH = `/sites/${STOPPED_SITE}`;
    const MODULE = 'jahia-dashboard-docs';
    const scenario = (name: string) => runFixture('checks/UndeployedModulesReferencesCheck-stopped.groovy', {SITEKEY: STOPPED_SITE, SCENARIO: name});
    const installedModules = (): Cypress.Chainable<string[]> =>
        graphql(`{ jcr { nodeByPath(path: "${STOPPED_SITE_PATH}") { property(name: "j:installedModules") { values } } } }`).its('jcr.nodeByPath.property.values');

    before(() => {
        createTestSite(STOPPED_SITE);
        scenario('STOP');
    });

    after(() => {
        scenario('START');
        deleteTestSite(STOPPED_SITE);
    });

    it('does not unreference a module which is installed but not started, and tells to start it', () => {
        scan(STOPPED_SITE_PATH, CHECKS).then(results => {
            const error = expectError(results, 'UNDEPLOYED_MODULE_ON_SITE', STOPPED_SITE_PATH);
            expectExtraInfo(error, 'module', MODULE);
            fixError(results.resultsId, error.id).then(result => {
                expect(result.fixed, 'fixed').to.be.false;
                expect(result.message).to.contain(`The module ${MODULE} is installed but not available`);
            });
        });
        installedModules().should('include', MODULE);
    });

    it('reports the error as fixed once the module is started again, and keeps it on the site', () => {
        scan(STOPPED_SITE_PATH, CHECKS).then(results => {
            const error = expectError(results, 'UNDEPLOYED_MODULE_ON_SITE', STOPPED_SITE_PATH);
            scenario('START');
            fixError(results.resultsId, error.id).its('fixed').should('be.true');
        });
        scan(STOPPED_SITE_PATH, CHECKS).then(results => expectNoError(results, 'UNDEPLOYED_MODULE_ON_SITE', STOPPED_SITE_PATH));
        installedModules().should('include', MODULE);
    });
});
