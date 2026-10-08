import {createSite, deleteSite} from '@jahia/cypress';

/**
 * Helpers to drive the content-integrity GraphQL API: run a scan, read its errors, fix them and configure the checks.
 * The scans run in the background on the server, so a scan is polled until it ends.
 */

export type Workspace = 'EDIT' | 'LIVE' | 'BOTH';

export type IntegrityError = {
    id: string;
    checkName: string;
    errorType: string;
    workspace: string;
    nodePath: string;
    nodeId: string;
    locale: string;
    message: string;
    fixed: boolean;
    fixable: boolean;
    fixWithValues: boolean;
    extraInfos: { label: string; value: string }[];
};

export type ScanResults = {
    resultsId: string | null;
    errors: IntegrityError[];
};

export type FixResult = {
    fixed: boolean;
    message?: string;
};

export type PathMatcher = string | RegExp;

const SCAN_POLLING_INTERVAL = 500;
const SCAN_POLLING_ATTEMPTS = 240;

const ERROR_FIELDS = 'id checkName errorType workspace nodePath nodeId locale message fixed fixable fixWithValues extraInfos { label value }';

const request = (query: string, variables: Record<string, unknown> = {}): Cypress.Chainable<Cypress.Response<any>> => cy.request({
    method: 'POST',
    url: '/modules/graphql',
    auth: {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')},
    headers: {Origin: Cypress.config().baseUrl},
    body: {query, variables},
    log: false
});

/**
 * Runs a GraphQL query as root, and fails the test if the response carries errors.
 */
export const graphql = (query: string, variables: Record<string, unknown> = {}): Cypress.Chainable<any> =>
    request(query, variables).then(response => {
        expect(response.body.errors, `GraphQL errors: ${JSON.stringify(response.body.errors)}`).to.be.undefined;
        return response.body.data;
    });

/**
 * Runs the fixtures script of the test, with the replacements, and fails the test if the script fails.
 */
export const runFixture = (script: string, replacements: Record<string, string> = {}): void => {
    cy.executeGroovy(`groovy/${script}`, replacements).then((result: unknown) => {
        expect(result, `Result of the script ${script}`).to.equal('.installed');
    });
};

/**
 * Creates the site used by the tests of a check: the default template set, with the languages en and fr.
 */
export const createTestSite = (siteKey: string): void => {
    deleteSite(siteKey);
    createSite(siteKey, {templateSet: 'templates-system', serverName: 'localhost', locale: 'en', languages: 'en,fr'});
};

export const deleteTestSite = (siteKey: string): void => {
    deleteSite(siteKey);
};

const waitForScan = (executionId: string, attempt = 0): Cypress.Chainable<string | null> =>
    graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { status resultsID logs } } }', {id: executionId})
        .then(data => {
            const {status, resultsID, logs} = data.integrity.scan;
            if (status === 'running') {
                expect(attempt, `The scan ${executionId} is still running`).to.be.lessThan(SCAN_POLLING_ATTEMPTS);
                cy.wait(SCAN_POLLING_INTERVAL, {log: false});
                return waitForScan(executionId, attempt + 1);
            }

            expect(status, `Status of the scan ${executionId}. Logs: ${(logs || []).join(' | ')}`).to.equal('finished');
            return cy.wrap(resultsID as string | null, {log: false});
        });

export type Execution = {
    id: string;
    status: string;
    startDate: string | null;
    resultsID: string | null;
    logs: string[];
    reports: { name: string; extension: string; location: string; uri: string }[] | null;
};

export type ScanParameters = {
    startNode: string;
    checks: string[];
    workspace?: Workspace;
    excludedPaths?: string[];
    upload?: boolean;
};

/**
 * Starts a scan, without waiting for its end, and returns the ID of its execution.
 */
export const startScan = ({startNode, checks, workspace = 'EDIT', excludedPaths, upload}: ScanParameters): Cypress.Chainable<string> =>
    graphql('query($workspace: WorkspaceToScan!, $startNode: String, $excludedPaths: [String], $checks: [String], $upload: Boolean) { integrity: contentIntegrity { scan: integrityScan { id: scan(workspace: $workspace, startNode: $startNode, excludedPaths: $excludedPaths, checksToRun: $checks, uploadResults: $upload) } } }',
        {workspace, startNode, excludedPaths, checks, upload}).then(data => data.integrity.scan.id);

/**
 * Reads an execution. Without ID, the API returns the scan which runs, or else the last one.
 */
export const readExecution = (id?: string): Cypress.Chainable<Execution> =>
    graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { id status startDate resultsID logs reports { name extension location uri } } } }', {id})
        .then(data => data.integrity.scan as Execution);

/**
 * Waits for the end of an execution, whatever its outcome, and returns it.
 */
export const waitForExecution = (id: string, attempt = 0): Cypress.Chainable<Execution> => readExecution(id).then(execution => {
    if (execution.status === 'running') {
        expect(attempt, `The scan ${id} is still running`).to.be.lessThan(SCAN_POLLING_ATTEMPTS);
        cy.wait(SCAN_POLLING_INTERVAL, {log: false});
        return waitForExecution(id, attempt + 1);
    }

    return cy.wrap(execution, {log: false});
});

// The scan reports are stored under the system site, and their number grows with every scan
export const REPORTS_PATH = '/sites/systemsite/files/content-integrity-reports';

/**
 * A scan which lasts while a test acts, once CiSlowCheck is registered. It excludes the stored reports, so that its length
 * does not grow with the number of scans run before.
 */
export const SLOW_SCAN: ScanParameters = {startNode: '/sites/systemsite', checks: ['CiSlowCheck'], excludedPaths: [REPORTS_PATH]};

/**
 * Stops the scan which runs, if any, and waits for its end: a test which fails while a scan runs must not leave it
 * running, as no other scan could start.
 */
export const stopRunningScan = (): void => {
    readExecution().then(execution => {
        if (execution?.status === 'running') {
            graphql('query($id: String) { integrity: contentIntegrity { scan: integrityScan(id: $id) { stopRunningScan } } }', {id: execution.id});
            waitForExecution(execution.id);
        }
    });
};

export type ScanProgress = {
    id: string;
    status: string;
    startDate: string | null;
    resultsID: string | null;
    logs: string[];
};

export type SubscriptionOutcome = {
    events: ScanProgress[];
    completed: boolean;
    errors: string[];
};

/**
 * Follows a scan through the subscription contentIntegrityScan, as the administration page does: over the GraphQL
 * WebSocket endpoint, with the protocol of subscriptions-transport-ws and the session of the page. Resolves once the
 * server ends the subscription, refuses it, or after the timeout. A page of the server must be opened first.
 */
export const followScan = (id: string, timeoutMs = 120000): Cypress.Chainable<SubscriptionOutcome> =>
    cy.window({log: false}).then({timeout: timeoutMs + 5000}, win => new Cypress.Promise<SubscriptionOutcome>(resolve => {
        const outcome: SubscriptionOutcome = {events: [], completed: false, errors: []};
        const socket = new win.WebSocket(`${Cypress.config().baseUrl.replace(/^http/, 'ws')}/modules/graphqlws`, 'graphql-ws');
        const timer = setTimeout(() => socket.close(), timeoutMs);
        socket.onopen = () => socket.send(JSON.stringify({type: 'connection_init', payload: {}}));
        socket.onmessage = message => {
            const data = JSON.parse(message.data);
            if (data.type === 'connection_ack') {
                socket.send(JSON.stringify({
                    id: '1',
                    type: 'start',
                    payload: {query: 'subscription($id: String!) { contentIntegrityScan(id: $id) { id status startDate resultsID logs } }', variables: {id}}
                }));
            } else if (data.type === 'data' && data.payload?.data?.contentIntegrityScan) {
                outcome.events.push(data.payload.data.contentIntegrityScan);
            } else if (data.type === 'data' || data.type === 'error') {
                outcome.errors.push(...(data.payload?.errors || [data.payload]).map((e: { message?: string }) => e?.message || JSON.stringify(e)));
                socket.close();
            } else if (data.type === 'complete') {
                outcome.completed = true;
                socket.close();
            }
        };
        socket.onclose = () => {
            clearTimeout(timer);
            resolve(outcome);
        };
    }));

export type StoredReport = {
    path: string;
    status: string | null;
    files: string[];
    fixedErrors: string[];
};

/**
 * The report node of results in the JCR, found by their identifier, or null.
 */
export const readStoredReport = (resultsId: string): Cypress.Chainable<StoredReport | null> =>
    graphql(`{ jcr { nodesByQuery(query: "SELECT * FROM [integrity:scanReport] WHERE [integrity:resultsId] = '${resultsId}'", queryLanguage: SQL2) {
        nodes { path status: property(name: "integrity:status") { value } fixed: property(name: "integrity:fixedErrors") { values } children { nodes { name } } } } } }`)
        .then(data => {
            const node = data.jcr.nodesByQuery.nodes[0];
            return node ? {
                path: node.path,
                status: node.status?.value ?? null,
                files: node.children.nodes.map((c: { name: string }) => c.name),
                fixedErrors: node.fixed?.values ?? []
            } : null;
        });

/**
 * Registers CiSlowCheck, a check which finds no error but waits on every node: a scan with it lasts as long as a test
 * needs to act while it runs. SLOW_SCAN with a delay of 200 ms lasts about 15 seconds.
 */
export const registerSlowCheck = (delayMs = 200): void => runFixture('scan/slowCheck.groovy', {DELAY: String(delayMs)});

export const unregisterSlowCheck = (): void => runFixture('scan/slowCheck-cleanup.groovy');

/**
 * Reads all the errors of scan results.
 */
export const getErrors = (resultsId: string): Cypress.Chainable<IntegrityError[]> =>
    graphql(`query($id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $id) { errorCount errors(offset: 0, pageSize: 10000) { ${ERROR_FIELDS} } } } }`, {id: resultsId})
        .then(data => data.integrity.results.errors as IntegrityError[]);

/**
 * Scans the subtree of a node with the given checks, waits for the end of the scan and returns its errors.
 * The checks are run even when they are disabled by default.
 */
export const scan = (startNode: string, checks: string[], workspace: Workspace = 'EDIT'): Cypress.Chainable<ScanResults> => {
    cy.log(`Scan of ${startNode} in ${workspace} with ${checks.join(', ')}`);
    return graphql('query($workspace: WorkspaceToScan!, $startNode: String, $checks: [String]) { integrity: contentIntegrity { scan: integrityScan { id: scan(workspace: $workspace, startNode: $startNode, checksToRun: $checks) } } }',
        {workspace, startNode, checks})
        .then(data => waitForScan(data.integrity.scan.id))
        .then(resultsId => {
            if (!resultsId) {
                return cy.wrap({resultsId: null, errors: []} as ScanResults, {log: false});
            }

            return getErrors(resultsId).then(errors => ({resultsId, errors} as ScanResults));
        });
};

const matchesPath = (error: IntegrityError, path?: PathMatcher): boolean => {
    if (path === undefined) {
        return true;
    }

    return typeof path === 'string' ? error.nodePath === path : path.test(error.nodePath);
};

/**
 * The errors of a type, optionally on a node.
 */
export const errorsOf = (results: ScanResults, errorType: string, path?: PathMatcher): IntegrityError[] =>
    results.errors.filter(e => e.errorType === errorType && matchesPath(e, path));

const describeErrors = (results: ScanResults): string => results.errors.map(e => `${e.errorType} on ${e.nodePath}`).join(', ') || 'no error';

/**
 * Asserts that the scan has detected an error of the type on the node, and returns it.
 */
export const expectError = (results: ScanResults, errorType: string, path: PathMatcher): IntegrityError => {
    const errors = errorsOf(results, errorType, path);
    expect(errors, `${errorType} on ${path}, among: ${describeErrors(results)}`).to.have.length.greaterThan(0);
    return errors[0];
};

/**
 * Asserts that the scan has not detected any error of the type on the node.
 */
export const expectNoError = (results: ScanResults, errorType: string, path: PathMatcher): void => {
    expect(errorsOf(results, errorType, path), `${errorType} on ${path}, among: ${describeErrors(results)}`).to.have.length(0);
};

/**
 * Asserts that the error carries the extra information, with the value when one is given.
 */
export const expectExtraInfo = (error: IntegrityError, label: string, value?: string | RegExp): void => {
    const info = error.extraInfos.find(i => i.label === label);
    expect(info, `Extra information ${label} of ${error.errorType}`).to.not.be.undefined;
    if (value instanceof RegExp) {
        expect(info.value).to.match(value);
    } else if (value !== undefined) {
        expect(info.value).to.equal(value);
    }
};

/**
 * Fixes an error of scan results, with the fix of the check which has detected it. The values are the ones typed by
 * an administrator, for the errors fixed with values. A rejected value is returned as a message, not as a test failure.
 */
export const fixError = (resultsId: string, errorId: string, values?: string[]): Cypress.Chainable<FixResult> =>
    request('query($resultsId: String, $id: String!, $values: [String]) { integrity: contentIntegrity { results: scanResultsDetails(id: $resultsId) { error: fixError(id: $id, values: $values) { fixed } } } }',
        {resultsId, id: errorId, values})
        .then(response => {
            if (response.body.errors?.length) {
                return {fixed: false, message: response.body.errors.map((e: { message: string }) => e.message).join(', ')};
            }

            return {fixed: response.body.data.integrity.results.error.fixed === true};
        });

/**
 * Scans, fixes the error of the type on the node, then scans again to check that the error is gone.
 */
export const fixAndVerify = (startNode: string, checks: string[], workspace: Workspace, errorType: string, path: PathMatcher): void => {
    scan(startNode, checks, workspace).then(results => {
        const error = expectError(results, errorType, path);
        expect(error.fixable, `${errorType} is fixable`).to.be.true;
        fixError(results.resultsId, error.id).then(result => {
            expect(result.fixed, `Fix of ${errorType} on ${error.nodePath}${result.message ? ': ' + result.message : ''}`).to.be.true;
        });
    });
    scan(startNode, checks, workspace).then(results => expectNoError(results, errorType, path));
};

/**
 * Asserts that the fix of the check does not fix the error, because the right value can't be guessed.
 */
export const expectFixFails = (startNode: string, checks: string[], workspace: Workspace, errorType: string, path: PathMatcher): void => {
    scan(startNode, checks, workspace).then(results => {
        const error = expectError(results, errorType, path);
        fixError(results.resultsId, error.id).then(result => expect(result.fixed, `Fix of ${errorType} on ${error.nodePath}`).to.be.false);
    });
    scan(startNode, checks, workspace).then(results => expectError(results, errorType, path));
};

export const configureCheck = (checkId: string, name: string, value: string): void => {
    graphql('query($id: String, $name: String!, $value: String!) { integrity: contentIntegrity { check: integrityCheckById(id: $id) { configure(name: $name, value: $value) } } }',
        {id: checkId, name, value})
        .then(data => expect(data.integrity.check.configure, `Configuration ${name}=${value} of ${checkId}`).to.be.true);
};

export const resetCheckConfiguration = (checkId: string): void => {
    graphql('query($id: String) { integrity: contentIntegrity { check: integrityCheckById(id: $id) { resetAllConfigurations } } }', {id: checkId});
};

/**
 * Asserts that the check which has detected the error does not provide any fix.
 */
export const expectNotFixable = (results: ScanResults, errorType: string, path: PathMatcher): void => {
    const error = expectError(results, errorType, path);
    expect(error.fixable, `${errorType} is fixable`).to.be.false;
};

/**
 * Asserts the status of a check: the checks disabled by default are run only when they are explicitly selected.
 */
export const expectCheckEnabled = (checkId: string, enabled: boolean): void => {
    graphql('query($id: String) { integrity: contentIntegrity { check: integrityCheckById(id: $id) { id enabled } } }', {id: checkId})
        .then(data => expect(data.integrity.check.enabled, `${checkId} is enabled`).to.equal(enabled));
};
