import {createTestSite, deleteTestSite, getErrors, graphql, resetCheckConfiguration, rootUser, runFixture, scan} from '../../support/integrity';

const SITE = 'ciKarafCommands';
const ROOT = `/sites/${SITE}/contents/live-properties`;
const OUTPUT = '/sites/systemsite/files/ci-karaf';
const MOUNT = 'ciKarafVirtualNodes';

/**
 * Runs a Karaf shell command, and yields what it prints. Several commands separated by ';' share the shell session, so
 * that `jcr:cd` sets the node from which `jcr:integrity-check` scans.
 */
const karaf = (command: string): Cypress.Chainable<string> => {
    runFixture('shell/karafCommand.groovy', {KARAF_COMMAND: command});
    return cy.request({
        url: `/files/default${OUTPUT}/output.txt?t=${Date.now()}`,
        auth: rootUser(),
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
        // A mount point: BinaryPropertiesSanityCheck reports the empty file of its folder, on a virtual node
        runFixture('virtualNodes/vfsMount.groovy', {MOUNT_NAME: MOUNT});
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
        runFixture('virtualNodes/vfsMount-cleanup.groovy', {MOUNT_NAME: MOUNT});
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

    it('prints the conditions of execution of the checks: jcr:integrity-printChecks -l full', () => {
        // PagesSanityCheck applies only to the nodes of type jmix:hasTemplateNode, which only the full output prints
        const pagesLine = (output: string) => output.split('\n').find(line => line.includes('PagesSanityCheck'));
        karaf('jcr:integrity-printChecks').then(output => expect(pagesLine(output)).not.to.contain('node type = jmix:hasTemplateNode'));
        karaf('jcr:integrity-printChecks -l full').then(output => expect(pagesLine(output)).to.contain('node type = jmix:hasTemplateNode'));
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

    it('skips the excluded paths: jcr:integrity-check -x', () => {
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck -x ${ROOT}/with-value`).then(output => {
            expect(output).to.contain(`Skipping node ${ROOT}/with-value`);
            expect(output).not.to.contain(withValue);
            expect(output).to.contain(withoutValue);
        });
    });

    it('prints a limited number of errors: jcr:integrity-check -l', () => {
        karaf(`jcr:cd ${ROOT} ; jcr:integrity-check -c LivePropertiesCheck -l 1`).then(output => {
            expect(output).to.contain('Printed the first 1 errors. Total number of errors: 2');
            expect(output.split('\n').filter(line => line.includes('LivePropertiesCheck')), output).to.have.length(1);
        });
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

    it('skips the virtual nodes: jcr:integrity-check --skipMP', () => {
        // The scan starts above the mount point: the virtual nodes are skipped among the children of the scanned nodes
        uuidOf(`/mounts/${MOUNT}/empty.txt/jcr:content`).then(uuid => {
            karaf('jcr:cd /mounts ; jcr:integrity-check -c BinaryPropertiesSanityCheck').should('contain', uuid);
            karaf('jcr:cd /mounts ; jcr:integrity-check -c BinaryPropertiesSanityCheck --skipMP').then(output => {
                expect(output).to.contain('Content integrity tested in');
                expect(output).not.to.contain(uuid);
            });
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

    // Runs last: it fixes the error left on the content by the fix of the latest scan
    it('loads the results of a given scan: jcr:integrity-printTestResults <id>, -l, printError -t and fix -t', () => {
        scan(ROOT, ['LivePropertiesCheck']).then(({resultsId, errors}) => {
            expect(errors, 'The error left by the fix of the latest scan').to.have.length(1);
            // A newer scan, of other content: the commands load the given scan, not the latest one
            karaf('jcr:cd /mounts ; jcr:integrity-check -c BinaryPropertiesSanityCheck');
            karaf(`jcr:integrity-printTestResults ${resultsId}`).then(output => {
                expect(output).to.contain('LivePropertiesCheck');
                expect(output).not.to.contain('BinaryPropertiesSanityCheck');
            });
            karaf(`jcr:integrity-printTestResults -l 0 ${resultsId}`).should('contain', 'Printed the first 0 errors. Total number of errors: 1');
            karaf(`jcr:integrity-printError -t ${resultsId} 0`).should('match', new RegExp(`Path\\s+.*${errors[0].nodePath}`));
            karaf(`jcr:integrity-fix -t ${resultsId} 0`).should('contain', 'Fixed the error id=0');
            getErrors(resultsId as string).then(stored => expect(stored[0].fixed).to.be.true);
        });
    });
});
