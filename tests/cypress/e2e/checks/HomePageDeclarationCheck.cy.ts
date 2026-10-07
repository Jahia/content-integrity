import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectFixFails, expectNoError, fixAndVerify, runFixture, scan} from '../../support/integrity';

const SITE = 'ciHomePageCheck';
const SITE_PATH = `/sites/${SITE}`;
const CHECKS = ['HomePageDeclarationCheck'];

// Each scenario of the fixtures script restores a regular home page first
const scenario = (name: string) => runFixture('checks/HomePageDeclarationCheck.groovy', {SITEKEY: SITE, SCENARIO: name});

describe('HomePageDeclarationCheck', () => {
    before(() => createTestSite(SITE));

    after(() => deleteTestSite(SITE));

    it('does not report a site with a single page flagged as home', () => {
        scenario('RESTORE');
        scan(SITE_PATH, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === SITE_PATH)).to.have.length(0));
    });

    describe('Detection', () => {
        it('detects MULTIPLE_HOMES on a site with two pages flagged as home', () => {
            scenario('MULTIPLE_HOMES');
            scan(SITE_PATH, CHECKS).then(results => {
                expectExtraInfo(expectError(results, 'MULTIPLE_HOMES', SITE_PATH), 'nb-pages-flagged', '2');
            });
        });

        it('detects FALLBACK_ON_NAME on a site without page flagged as home, but with a page named home', () => {
            scenario('FALLBACK_ON_NAME');
            scan(SITE_PATH, CHECKS).then(results => expectError(results, 'FALLBACK_ON_NAME', SITE_PATH));
        });

        it('detects NO_HOME on a site without page flagged as home, and without page named home', () => {
            scenario('NO_HOME');
            scan(SITE_PATH, CHECKS).then(results => expectError(results, 'NO_HOME', SITE_PATH));
        });

        it('does not report NO_HOME in live, where the home page may be not published yet', () => {
            scenario('NO_HOME');
            scan(SITE_PATH, CHECKS, 'LIVE').then(results => expectNoError(results, 'NO_HOME', SITE_PATH));
        });

        it('detects FALLBACK_ON_NAME_WRONG_TYPE on a site whose sub-node named home is not a page', () => {
            scenario('FALLBACK_ON_NAME_WRONG_TYPE');
            scan(SITE_PATH, CHECKS).then(results => {
                expectExtraInfo(expectError(results, 'FALLBACK_ON_NAME_WRONG_TYPE', SITE_PATH), 'home-node-type', 'jnt:contentFolder');
            });
        });
    });

    describe('Fix', () => {
        it('fixes MULTIPLE_HOMES by keeping a single page flagged as home', () => {
            scenario('MULTIPLE_HOMES');
            fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'MULTIPLE_HOMES', SITE_PATH);
        });

        it('fixes FALLBACK_ON_NAME by flagging the page named home', () => {
            scenario('FALLBACK_ON_NAME');
            fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'FALLBACK_ON_NAME', SITE_PATH);
        });

        it('fixes NO_HOME by flagging a page of the site', () => {
            scenario('NO_HOME');
            fixAndVerify(SITE_PATH, CHECKS, 'EDIT', 'NO_HOME', SITE_PATH);
        });

        it('does not fix FALLBACK_ON_NAME_WRONG_TYPE', () => {
            scenario('FALLBACK_ON_NAME_WRONG_TYPE');
            expectFixFails(SITE_PATH, CHECKS, 'EDIT', 'FALLBACK_ON_NAME_WRONG_TYPE', SITE_PATH);
        });
    });
});
