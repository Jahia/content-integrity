import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectFixFails,
    fixAndVerify,
    resetCheckConfiguration,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciAceCheck';
const SITE_PATH = `/sites/${SITE}`;
const ROOT = `${SITE_PATH}/contents/ace`;
const CHECK = 'AceSanityCheck';
const CHECKS = [CHECK];

// See the fixtures script: each scenario is a node named after the error type, granted to its own user when it alters an external ACE
const user = (suffix: string) => `ci-${SITE}-${suffix}`;
const ace = (scenario: string, username: string) => `${ROOT}/${scenario}/j:acl/GRANT_u_${username}`;
const externalAce = (username: string) => `${SITE_PATH}/j:acl/REFeditor_currentSite-access_u_${username}`;

const regularAces: [string, string][] = [
    ['NO_PRINCIPAL', ace('NO_PRINCIPAL', user('ace'))],
    ['NO_ACE_TYPE_PROP', ace('NO_ACE_TYPE_PROP', user('ace'))],
    ['NO_ROLES_PROP', ace('NO_ROLES_PROP', user('ace'))],
    ['INVALID_PRINCIPAL', `${ROOT}/INVALID_PRINCIPAL/j:acl/GRANT_u_ci-user-which-does-not-exist`],
    ['INVALID_NODENAME', `${ROOT}/INVALID_NODENAME/j:acl/GRANT_u_another-name`],
    ['ROLE_DOESNT_EXIST', ace('ROLE_DOESNT_EXIST', user('ace'))],
    ['MISSING_SITE_PRIVILEGED_GRP_MEMBER', ace('MISSING_SITE_PRIVILEGED_GRP_MEMBER', user('ace-privileged'))],
    ['MISSING_EXTERNAL_ACE', ace('MISSING_EXTERNAL_ACE', user('ace-0'))],
    ['ACE_NON_GRANT_WITH_EXTERNAL_ACE', ace('ACE_NON_GRANT_WITH_EXTERNAL_ACE', user('ace-1'))]
];
const externalAces: [string, string][] = [
    ['SOURCE_ACE_NOT_TYPE_GRANT', externalAce(user('ace-1'))],
    ['INVALID_ACE_TYPE_PROP', externalAce(user('ace-2'))],
    ['NO_SOURCE_ACE_PROP', externalAce(user('ace-3'))],
    ['EMPTY_SOURCE_ACE_PROP', externalAce(user('ace-4'))],
    ['SOURCE_ACE_BROKEN_REF', externalAce(user('ace-5'))],
    ['INVALID_EXTERNAL_PERMISSIONS', externalAce(user('ace-6'))],
    ['INVALID_EXTERNAL_ACE_PATH', `${ROOT}/external-ace-holder/j:acl/REFeditor_currentSite-access_u_${user('ace-7')}`],
    ['DUPLICATED_REF_SRC_ACE', externalAce(user('ace-8'))],
    ['INVALID_ROLES_PROP', externalAce(user('ace-9'))],
    ['ROLES_DIFFER_ON_SOURCE_ACE', externalAce(user('ace-10'))]
];
const errorPath = (errorType: string) => [...regularAces, ...externalAces].find(([type]) => type === errorType)[1];

describe('AceSanityCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/AceSanityCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
        runFixture('checks/AceSanityCheck-cleanup.groovy', {SITEKEY: SITE});
    });

    describe('Detection', () => {
        let results: ScanResults;

        before(() => {
            scan(SITE_PATH, CHECKS).then(r => {
                results = r;
            });
        });

        regularAces.forEach(([errorType, path]) => {
            it(`detects ${errorType} on the ACE`, () => {
                expectError(results, errorType, path);
            });
        });

        externalAces.forEach(([errorType, path]) => {
            it(`detects ${errorType} on the external ACE`, () => {
                expectError(results, errorType, path);
            });
        });

        it('reports the expected name of an ACE with an invalid name', () => {
            expectExtraInfo(expectError(results, 'INVALID_NODENAME', errorPath('INVALID_NODENAME')), 'expected-nodename', `GRANT_u_${user('ace')}`);
        });

        it('reports the role which does not exist', () => {
            expectExtraInfo(expectError(results, 'ROLE_DOESNT_EXIST', errorPath('ROLE_DOESNT_EXIST')), 'role', 'ci-role-which-does-not-exist');
        });

        it('reports the expected path of an external ACE at an invalid location', () => {
            expectExtraInfo(expectError(results, 'INVALID_EXTERNAL_ACE_PATH', errorPath('INVALID_EXTERNAL_ACE_PATH')),
                'external-ace-expected-path', externalAce(user('ace-7')));
        });

        it('does not raise a framework error on an ACE without principal or type', () => {
            expect(results.errors.filter(e => e.errorType === 'FRAMEWORK_ERROR' && e.nodePath.startsWith(ROOT))).to.have.length(0);
        });
    });

    describe('TOO_MANY_ACE', () => {
        afterEach(() => resetCheckConfiguration(CHECK));

        it('is not raised under the threshold', () => {
            scan(SITE_PATH, CHECKS).then(results => {
                expect(results.errors.filter(e => e.errorType === 'TOO_MANY_ACE')).to.have.length(0);
            });
        });

        it('is raised on the site once the number of ACE reaches the threshold', () => {
            configureCheck(CHECK, 'ace-count-threshold', '5');
            scan(SITE_PATH, CHECKS).then(results => {
                expectExtraInfo(expectError(results, 'TOO_MANY_ACE', SITE_PATH), 'ace-count');
            });
        });
    });

    describe('Fix', () => {
        // These errors need an analysis: the check provides no fix for them
        ['INVALID_NODENAME', 'NO_ROLES_PROP', 'ROLE_DOESNT_EXIST', 'MISSING_SITE_PRIVILEGED_GRP_MEMBER', 'ACE_NON_GRANT_WITH_EXTERNAL_ACE',
            'INVALID_EXTERNAL_PERMISSIONS', 'DUPLICATED_REF_SRC_ACE', 'INVALID_ROLES_PROP'].forEach(errorType => {
            it(`does not fix ${errorType}`, () => {
                expectFixFails(SITE_PATH, CHECKS, 'EDIT', errorType, errorPath(errorType));
            });
        });

        ['NO_PRINCIPAL', 'NO_ACE_TYPE_PROP', 'INVALID_PRINCIPAL', 'MISSING_EXTERNAL_ACE', 'SOURCE_ACE_NOT_TYPE_GRANT', 'INVALID_ACE_TYPE_PROP',
            'NO_SOURCE_ACE_PROP', 'EMPTY_SOURCE_ACE_PROP', 'SOURCE_ACE_BROKEN_REF', 'INVALID_EXTERNAL_ACE_PATH', 'ROLES_DIFFER_ON_SOURCE_ACE'].forEach(errorType => {
            it(`fixes ${errorType}`, () => {
                fixAndVerify(SITE_PATH, CHECKS, 'EDIT', errorType, errorPath(errorType));
            });
        });
    });
});
