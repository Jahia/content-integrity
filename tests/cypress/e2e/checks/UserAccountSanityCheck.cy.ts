import {expectNotFixable, runFixture, scan} from '../../support/integrity';

// No site is needed: the user is created at server level. The suffix makes its name unique
const SUFFIX = 'ciUserAccountCheck';
const CHECKS = ['UserAccountSanityCheck'];
// The path of a user is hashed: /users/xx/yy/zz/username
const USER = new RegExp(`/ci-${SUFFIX}-user$`);

describe('UserAccountSanityCheck', () => {
    before(() => runFixture('checks/UserAccountSanityCheck.groovy', {SITEKEY: SUFFIX}));

    after(() => runFixture('checks/UserAccountSanityCheck-cleanup.groovy', {SITEKEY: SUFFIX}));

    it('detects NOT_OWNER on a user which is not owner of its account node', () => {
        scan('/users', CHECKS).then(results => expectNotFixable(results, 'NOT_OWNER', USER));
    });

    it('does not report the root user', () => {
        scan('/users', CHECKS).then(results => expect(results.errors.filter(e => /\/root$/.test(e.nodePath))).to.have.length(0));
    });
});
