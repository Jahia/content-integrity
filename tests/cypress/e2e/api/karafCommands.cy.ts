import {createTestSite, deleteTestSite, graphql, resetCheckConfiguration, runFixture} from '../../support/integrity';

const SITE = 'ciKarafCommands';
const ROOT = `/sites/${SITE}/contents/live-properties`;
const OUTPUT = '/sites/systemsite/files/ci-karaf';

/**
 * Runs a Karaf shell command, and yields what it prints. Several commands separated by ';' share the shell session, so
 * that `jcr:cd` sets the node from which `jcr:integrity-check` scans.
 */
const karaf = (command: string): Cypress.Chainable<string> => {
    runFixture('shell/karafCommand.groovy', {KARAF_COMMAND: command});
    return cy.request({
        url: `/files/default${OUTPUT}/output.txt?t=${Date.now()}`,
        auth: {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')},
        log: false
    }).its('body');
};

const uuidOf = (path: string): Cypress.Chainable<string> =>
    graphql('query($path: String!) { jcr { nodeByPath(path: $path) { uuid } } }', {path}).its('jcr.nodeByPath.uuid');

describe('Karaf shell commands', () => {
    let withValue: string;
    let withoutValue: string;

    before(() => {
        createTestSite(SITE);
        // LivePropertiesCheck is disabled by default: LIVE_PROPERTIES on with-value, EMPTY_LIVE_PROPERTIES on without-value
        runFixture('checks/LivePropertiesCheck.groovy', {SITEKEY: SITE});
        uuidOf(`${ROOT}/with-value`).then(uuid => {
            withValue = uuid;
        });
        uuidOf(`${ROOT}/without-value`).then(uuid => {
            withoutValue = uuid;
        });
    });

    after(() => {
        resetCheckConfiguration('FlatStorageCheck');
        karaf('jcr:integrity-configureCheck -id LivePropertiesCheck -e false');
        graphql('mutation($path: String!) { jcr { deleteNode(pathOrId: $path) } }', {path: OUTPUT});
        deleteTestSite(SITE);
    });

    it('lists the checks with their status: jcr:integrity-printChecks', () => {
        graphql('{ integrity: contentIntegrity { checks: integrityChecks { id enabled } } }').then(data => {
            const checks: { id: string; enabled: boolean }[] = data.integrity.checks;
            karaf('jcr:integrity-printChecks').then(output => {
                expect(output).to.contain(`Integrity checks (${checks.length}):`);
                checks.forEach(check => expect(output).to.match(new RegExp(`\\(id: ${check.id}, priority: [\\d.]+, enabled: ${check.enabled}\\)`)));
            });
        });
    });

    it('prints, sets and resets a parameter of a check: jcr:integrity-configureCheck', () => {
        karaf('jcr:integrity-configureCheck -id FlatStorageCheck -pc').should('match', /FlatStorageCheck:\n\s+threshold = 500 \(/);
        karaf('jcr:integrity-configureCheck -id FlatStorageCheck -p threshold -v 3').should('contain', 'FlatStorageCheck: threshold = 3');
        graphql('{ integrity: contentIntegrity { check: integrityCheckById(id: "FlatStorageCheck") { configuration(name: "threshold") { value } } } }')
            .its('integrity.check.configuration.value').should('equal', '3');
        karaf('jcr:integrity-configureCheck -id FlatStorageCheck -rp threshold').should('contain', 'FlatStorageCheck: threshold = 500');
        karaf('jcr:integrity-configureCheck -id LockSanityCheck -pc').should('contain', 'LockSanityCheck is not configurable');
        karaf('jcr:integrity-configureCheck -id NoSuchCheck -pc').should('contain', 'No check found for ID NoSuchCheck');
    });

    it('enables and disables a check: jcr:integrity-configureCheck -e', () => {
        karaf('jcr:integrity-configureCheck -id LivePropertiesCheck -e true').should('contain', 'LivePropertiesCheck: enabled');
        graphql('{ integrity: contentIntegrity { check: integrityCheckById(id: "LivePropertiesCheck") { enabled } } }')
            .its('integrity.check.enabled').should('be.true');
        karaf('jcr:integrity-configureCheck -id LivePropertiesCheck -e false').should('contain', 'LivePropertiesCheck: disabled');
    });

    it('scans the current node with the given checks, even a disabled one: jcr:integrity-check -c', () => {
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck`).then(output => {
            expect(output).to.contain('Content integrity tested in');
            expect(output).to.contain(withValue);
            expect(output).to.contain(withoutValue);
        });
    });

    it('skips the checks prefixed with a colon: jcr:integrity-check -c :<check>', () => {
        karaf('jcr:integrity-configureCheck -id LivePropertiesCheck -e true');
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c :LivePropertiesCheck`).then(output => {
            expect(output).to.contain('Content integrity tested in');
            expect(output).not.to.contain('LivePropertiesCheck');
        });
        // Without the skip, the enabled check reports the errors
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check`).should('contain', withValue);
        karaf('jcr:integrity-configureCheck -id LivePropertiesCheck -e false');
    });

    it('prints the results of the latest scan, then the details of an error, then fixes it by its ID', () => {
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck`);
        karaf('jcr:integrity-printTestResults').then(output => {
            // The rows are numbered from 0, in the order of the errors: the number is the ID of the error in the other commands
            const rows = output.split('\n').filter(line => line.includes('LivePropertiesCheck'));
            expect(rows, output).to.have.length(2);
            const firstUuid = rows[0].includes(withValue) ? withValue : withoutValue;
            const firstPath = firstUuid === withValue ? `${ROOT}/with-value` : `${ROOT}/without-value`;
            karaf('jcr:integrity-printError 0').then(details => {
                expect(details).to.match(new RegExp(`Path\\s+.*${firstPath}`));
                expect(details).to.match(/Check ID\s+.*LivePropertiesCheck/);
                expect(details).to.match(/Error type\s+.*(EMPTY_)?LIVE_PROPERTIES/);
            });
            karaf('jcr:integrity-printError 99').should('contain', "The specified error (id=99) couldn't be found");
            karaf('jcr:integrity-fix 0').should('contain', 'Fixed the error id=0');
            karaf('jcr:integrity-fix 0').should('contain', 'The error (id=0) is already fixed');
            // The fixed error is flagged in the column Fixed, then left out with -ef. The table of the shell draws its
            // columns with the box-drawing character │ on Jahia 8.1, and with | on Jahia 8.2
            karaf('jcr:integrity-printTestResults').then(results => {
                expect(results.split('\n').find(line => line.includes(firstUuid))).to.match(/[│|]\s*X\s*[│|]/);
            });
            karaf('jcr:integrity-printTestResults -ef').should('not.contain', firstUuid);
        });
        // A new scan does not report the fixed error anymore
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck`).then(output => {
            const remaining = output.split('\n').filter(line => line.includes('LivePropertiesCheck'));
            expect(remaining, output).to.have.length(1);
        });
    });

    it('uploads the reports of the latest scan in the JCR: jcr:integrity-printTestResults -d -u', () => {
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck`);
        karaf('jcr:integrity-printTestResults -d -u').should('not.contain', 'Failed to write');
        graphql('{ jcr { nodesByQuery(query: "SELECT * FROM [integrity:scanReport] ORDER BY [integrity:executionDate] DESC", queryLanguage: SQL2, limit: 1) { nodes { children { nodes { name } } } } } }')
            .then(data => {
                const files: string[] = data.jcr.nodesByQuery.nodes[0].children.nodes.map((c: { name: string }) => c.name);
                expect(files.some(name => name.endsWith('.csv')), files.join(', ')).to.be.true;
            });
    });
});
