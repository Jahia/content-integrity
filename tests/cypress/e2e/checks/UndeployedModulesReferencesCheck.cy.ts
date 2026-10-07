import {createTestSite, deleteTestSite, expectError, expectExtraInfo, fixAndVerify, runFixture, scan} from '../../support/integrity';

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
