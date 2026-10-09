import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoError,
    expectNoFix,
    fixAndVerify,
    fixError,
    graphql,
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
            it(`provides no fix for ${errorType}`, () => {
                expectNoFix(SITE_PATH, CHECKS, 'EDIT', errorType, errorPath(errorType));
            });
        });

        ['NO_PRINCIPAL', 'NO_ACE_TYPE_PROP', 'INVALID_PRINCIPAL', 'MISSING_EXTERNAL_ACE', 'SOURCE_ACE_NOT_TYPE_GRANT', 'INVALID_ACE_TYPE_PROP',
            'NO_SOURCE_ACE_PROP', 'EMPTY_SOURCE_ACE_PROP', 'SOURCE_ACE_BROKEN_REF', 'INVALID_EXTERNAL_ACE_PATH', 'ROLES_DIFFER_ON_SOURCE_ACE'].forEach(errorType => {
            it(`fixes ${errorType}`, () => {
                fixAndVerify(SITE_PATH, CHECKS, 'EDIT', errorType, errorPath(errorType));
            });
        });
    });

    describe('Roles chosen to fill the missing j:roles of an ACE', () => {
        const NO_ROLES = ace('NO_ROLES_PROP', user('ace'));
        const DEFINITIONS = ['PropertyDefinitionsSanityCheck'];

        type FixValues = { name: string; multiple: boolean; choices: string[]; choiceLabels: string[]; description: string };
        const readFixValues = (resultsId: string, errorId: string): Cypress.Chainable<FixValues> =>
            graphql('query($r: String, $id: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $r) { error: errorById(id: $id) { fixValues { name multiple choices choiceLabels description } } } } }',
                {r: resultsId, id: errorId}).then(data => data.integrity.results.error.fixValues as FixValues);

        before(() => runFixture('checks/AceSanityCheck.groovy', {SITEKEY: SITE}));

        it('offers the roles which can be given on content, with a description of what they grant', () => {
            scan(`${ROOT}/NO_ROLES_PROP`, DEFINITIONS).then(results => {
                const error = expectError(results, 'EMPTY_MANDATORY_PROPERTY', NO_ROLES);
                expect(error.fixable).to.be.true;
                readFixValues(results.resultsId, error.id).then(fixValues => {
                    expect(fixValues.name).to.equal('j:roles');
                    expect(fixValues.multiple).to.be.true;
                    expect(fixValues.choices).to.include.members(['editor', 'reader', 'owner']);
                    // A hidden role is set by the platform, a site or server role is not given on content
                    expect(fixValues.choices).to.not.include.members(['privileged']);
                    expect(fixValues.choices).to.not.include('site-administrator');
                    expect(fixValues.choices).to.not.include('server-administrator');
                    expect(fixValues.choiceLabels).to.have.length(fixValues.choices.length);
                    expect(fixValues.choiceLabels).to.include('Editor (editor)');
                    expect(fixValues.description).to.contain(`Choose the roles to grant to the user ${user('ace')} on this node and its sub-nodes`);
                });
            });
        });

        it('refuses a role which can not be given on content', () => {
            scan(`${ROOT}/NO_ROLES_PROP`, DEFINITIONS).then(results => {
                const error = expectError(results, 'EMPTY_MANDATORY_PROPERTY', NO_ROLES);
                ['server-administrator', 'ci-role-which-does-not-exist'].forEach(role => {
                    fixError(results.resultsId, error.id, [role]).then(result => {
                        expect(result.fixed).to.be.false;
                        expect(result.message).to.contain(`The role ${role} can't be given on this access control entry`);
                    });
                });
            });
        });

        it('fixes the ACE with the chosen roles', () => {
            scan(`${ROOT}/NO_ROLES_PROP`, DEFINITIONS).then(results => {
                const error = expectError(results, 'EMPTY_MANDATORY_PROPERTY', NO_ROLES);
                fixError(results.resultsId, error.id, ['reader', 'editor']).then(result => expect(result.fixed, result.message).to.be.true);
            });
            scan(`${ROOT}/NO_ROLES_PROP`, DEFINITIONS).then(results => expectNoError(results, 'EMPTY_MANDATORY_PROPERTY', NO_ROLES));
            scan(`${ROOT}/NO_ROLES_PROP`, CHECKS).then(results => expectNoError(results, 'NO_ROLES_PROP', NO_ROLES));
            graphql(`{ jcr { nodeByPath(path: "${NO_ROLES}") { property(name: "j:roles") { values } } } }`)
                .its('jcr.nodeByPath.property.values').should('deep.equal', ['reader', 'editor']);
        });
    });
});
