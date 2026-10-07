import {createTestSite, deleteTestSite, expectExtraInfo, expectNotFixable, runFixture, scan} from '../../support/integrity';

const SITE = 'ciSystemGroupsCheck';
const SITE_PATH = `/sites/${SITE}`;
const CHECKS = ['SiteLevelSystemGroupsCheck'];

const scenario = (name: string) => runFixture('checks/SiteLevelSystemGroupsCheck.groovy', {SITEKEY: SITE, SCENARIO: name});

describe('SiteLevelSystemGroupsCheck', () => {
    before(() => createTestSite(SITE));

    after(() => {
        scenario('RESTORE');
        deleteTestSite(SITE);
    });

    it('does not report a site with its system groups', () => {
        scan(SITE_PATH, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === SITE_PATH)).to.have.length(0));
    });

    it('detects MISSING_MEMBERSHIP when the group site-privileged is not a member of privileged', () => {
        scenario('MISSING_MEMBERSHIP');
        scan(SITE_PATH, CHECKS).then(results => {
            expectNotFixable(results, 'MISSING_MEMBERSHIP', SITE_PATH);
            const error = results.errors.find(e => e.errorType === 'MISSING_MEMBERSHIP');
            expectExtraInfo(error, 'missing-member', 'site-privileged');
            expectExtraInfo(error, 'group-missing-a-member', 'privileged');
        });
        scenario('RESTORE');
    });

    it('detects GROUP_DOES_NOT_EXIST when the group site-privileged of the site is deleted', () => {
        scenario('GROUP_DOES_NOT_EXIST');
        scan(SITE_PATH, CHECKS).then(results => {
            expectNotFixable(results, 'GROUP_DOES_NOT_EXIST', SITE_PATH);
            expectExtraInfo(results.errors.find(e => e.errorType === 'GROUP_DOES_NOT_EXIST'), 'group-name', 'site-privileged');
        });
    });
});
