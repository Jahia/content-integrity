import {createTestSite, deleteTestSite, expectError, expectNoError, fixAndVerify, runFixture, scan} from '../../support/integrity';

const SITE = 'ciMarkForDeletionCheck';
const ROOT = `/sites/${SITE}/contents/mark-for-deletion`;
const CHECKS = ['MarkForDeletionCheck'];
// The path of a user is hashed: /users/xx/yy/zz/username
const USER_FOLDER = new RegExp(`/ci-${SITE}/files/marked-for-deletion$`);

describe('MarkForDeletionCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        deleteTestSite(SITE);
        runFixture('checks/MarkForDeletionCheck-cleanup.groovy', {SITEKEY: SITE});
    });

    describe('Detection', () => {
        it('detects NO_ROOT_DELETION on a node flagged as deleted, without root of the deletion', () => {
            scan(ROOT, CHECKS).then(results => expectError(results, 'NO_ROOT_DELETION', `${ROOT}/no-root-deletion`));
        });

        it('does not report a node regularly marked for deletion', () => {
            scan(ROOT, CHECKS).then(results => expect(results.errors.filter(e => e.nodePath === `${ROOT}/regular-deletion`)).to.have.length(0));
        });

        it('detects DELETION_MARK_IN_LIVE on a node flagged as deleted in live', () => {
            scan(ROOT, CHECKS, 'LIVE').then(results => expectError(results, 'DELETION_MARK_IN_LIVE', `${ROOT}/deleted-in-live`));
        });

        it('does not report the live node of a node marked for deletion in default', () => {
            scan(ROOT, CHECKS, 'LIVE').then(results => expectNoError(results, 'DELETION_MARK_IN_LIVE', `${ROOT}/regular-deletion`));
        });

        it('detects DELETION_MARK_UNDER_USERS on a folder of a user marked for deletion', () => {
            scan('/users', CHECKS).then(results => expectError(results, 'DELETION_MARK_UNDER_USERS', USER_FOLDER));
        });
    });

    describe('Fix', () => {
        it('fixes NO_ROOT_DELETION by unmarking the node', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'NO_ROOT_DELETION', `${ROOT}/no-root-deletion`);
        });

        it('fixes DELETION_MARK_IN_LIVE by unmarking the live node', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'DELETION_MARK_IN_LIVE', `${ROOT}/deleted-in-live`);
        });

        it('fixes DELETION_MARK_UNDER_USERS by unmarking the folder', () => {
            fixAndVerify('/users', CHECKS, 'EDIT', 'DELETION_MARK_UNDER_USERS', USER_FOLDER);
        });
    });
});
