import {createTestSite, deleteTestSite, expectError, expectExtraInfo, expectNoError, fixAndVerify, graphql, runFixture, scan, ScanResults} from '../../support/integrity';

const SITE = 'ciLockCheck';
const ROOT = `/sites/${SITE}/contents/locks`;
const CHECKS = ['LockSanityCheck'];

describe('LockSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/LockSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(SITE));

    it('detects INCONSISTENT_LOCK on a node with only some of the lock properties', () => {
        const error = expectError(results, 'INCONSISTENT_LOCK', `${ROOT}/inconsistent-lock`);
        expectExtraInfo(error, 'missing-properties', /j:locktoken/);
    });

    it('detects DELETION_LOCK_ON_I18N on a translation node locked for deletion', () => {
        expectError(results, 'DELETION_LOCK_ON_I18N', `${ROOT}/deletion-lock-on-translation/j:translation_en`);
    });

    it('does not report a node which is not locked', () => {
        expectNoError(results, 'INCONSISTENT_LOCK', `${ROOT}/not-locked`);
    });

    it('fixes INCONSISTENT_LOCK', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'INCONSISTENT_LOCK', `${ROOT}/inconsistent-lock`);
    });

    it('fixes DELETION_LOCK_ON_I18N', () => {
        fixAndVerify(ROOT, CHECKS, 'EDIT', 'DELETION_LOCK_ON_I18N', `${ROOT}/deletion-lock-on-translation/j:translation_en`);
    });
});

describe('LockSanityCheck on the locks of the workflows', () => {
    const WORKFLOW_SITE = 'ciLockWorkflow';
    const LOCKS = `/sites/${WORKFLOW_SITE}/contents/workflow-locks`;
    let results: ScanResults;

    before(() => {
        createTestSite(WORKFLOW_SITE);
        runFixture('checks/LockSanityCheck-workflow.groovy', {SITEKEY: WORKFLOW_SITE});
        scan(LOCKS, CHECKS, 'BOTH').then(r => {
            results = r;
        });
    });

    after(() => deleteTestSite(WORKFLOW_SITE));

    const inWorkspace = (workspace: string) => (r: ScanResults): ScanResults => ({...r, errors: r.errors.filter(e => e.workspace === workspace)});

    it('detects LOCK_OF_UNKNOWN_PROCESS on a node locked for validation by a publication process which does not exist', () => {
        const error = expectError(inWorkspace('default')(results), 'LOCK_OF_UNKNOWN_PROCESS', `${LOCKS}/unknown-process`);
        expectExtraInfo(error, 'lock-type', ' publication-process-999999999 :validation');
        expectExtraInfo(error, 'process-id', '999999999');
    });

    it('detects LOCK_OF_UNKNOWN_PROCESS on a node locked by the lock step of a process which does not exist', () => {
        expectExtraInfo(expectError(results, 'LOCK_OF_UNKNOWN_PROCESS', `${LOCKS}/custom-lock-of-unknown-process`), 'lock-type', ' process-999999996 :custom');
    });

    it('detects LOCK_PROPERTIES_IN_LIVE on the nodes published for the first time while a workflow locks them', () => {
        const live = inWorkspace('live')(results);
        // The workflow has ended: the node is unlocked in default, but its lock properties stay in live
        expectExtraInfo(expectError(live, 'LOCK_PROPERTIES_IN_LIVE', `${LOCKS}/workflow-ended`), 'lock-types', /publication-process-999999998 :validation/);
        expectExtraInfo(expectError(live, 'LOCK_PROPERTIES_IN_LIVE', `${LOCKS}/unknown-process`), 'properties', /j:locktoken/);
        // Not reported as an inconsistent lock: in live, the lock itself is never published
        expectNoError(live, 'INCONSISTENT_LOCK', `${LOCKS}/workflow-ended`);
        expectNoError(results, 'LOCK_OF_UNKNOWN_PROCESS', `${LOCKS}/workflow-ended`);
    });

    it('does not report a lock of a user, nor a node already published when a workflow locks it', () => {
        ['LOCK_OF_UNKNOWN_PROCESS', 'INCONSISTENT_LOCK', 'LOCK_PROPERTIES_IN_LIVE'].forEach(type => {
            expectNoError(results, type, `${LOCKS}/user-lock`);
            expectNoError(results, type, `${LOCKS}/published-before-lock`);
        });
    });

    it('fixes LOCK_OF_UNKNOWN_PROCESS by releasing the lock of the process', () => {
        fixAndVerify(LOCKS, CHECKS, 'EDIT', 'LOCK_OF_UNKNOWN_PROCESS', `${LOCKS}/unknown-process`);
        fixAndVerify(LOCKS, CHECKS, 'EDIT', 'LOCK_OF_UNKNOWN_PROCESS', `${LOCKS}/custom-lock-of-unknown-process`);
        // Unlocked, the node has no lock property left
        scan(LOCKS, CHECKS, 'EDIT').then(r => {
            expectNoError(r, 'INCONSISTENT_LOCK', `${LOCKS}/unknown-process`);
            expectNoError(r, 'INCONSISTENT_LOCK', `${LOCKS}/custom-lock-of-unknown-process`);
        });
        graphql('query($p: String!) { jcr { nodeByPath(path: $p) { lockTypes: property(name: "j:lockTypes") { values } } } }', {p: `${LOCKS}/unknown-process`})
            .its('jcr.nodeByPath.lockTypes').should('be.null');
    });

    it('fixes LOCK_PROPERTIES_IN_LIVE by removing the lock properties of the live node', () => {
        fixAndVerify(LOCKS, CHECKS, 'LIVE', 'LOCK_PROPERTIES_IN_LIVE', `${LOCKS}/workflow-ended`);
        graphql('query($p: String!) { jcr(workspace: LIVE) { nodeByPath(path: $p) { lockTypes: property(name: "j:lockTypes") { values } token: property(name: "j:locktoken") { value } } } }', {p: `${LOCKS}/workflow-ended`})
            .its('jcr.nodeByPath').should('deep.equal', {lockTypes: null, token: null});
    });
});
