import {createTestSite, deleteTestSite, runFixture, scan} from '../../support/integrity';
import {closeDetailsPanel, getDetailsPanel, getRow, openErrorDetails, selectInDropdown, visitAdmin} from '../../support/adminPage';

const SITE = 'ciUiAclFix';
const USER = `ci-${SITE}-ace`;
// An ACE without j:principal, named after the user it was granted to
const NO_PRINCIPAL = `/sites/${SITE}/contents/ace/NO_PRINCIPAL/j:acl/GRANT_u_${USER}`;

describe('Fix of an access control entry in the administration page', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/AceSanityCheck.groovy', {SITEKEY: SITE});
        scan(`/sites/${SITE}/contents/ace/NO_PRINCIPAL`, ['AceSanityCheck']);
    });

    after(() => {
        runFixture('checks/AceSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    it('chooses the principal of an ACE in a dropdown which groups the users and the groups', () => {
        // The ACL errors do not block an XML import: the results open without filter
        visitAdmin();
        selectInDropdown('Error type', /^NO_PRINCIPAL/);
        openErrorDetails(NO_PRINCIPAL);
        getDetailsPanel().within(() => {
            cy.contains('has no principal').should('be.visible');
            // The user its name tells is suggested
            cy.get('.moonstone-dropdown').should('contain.text', USER);
            cy.contains('button', /^Fix$/).should('be.enabled');
        });
        // The menu of the dropdown is displayed above the page, outside of the panel
        getDetailsPanel().find('.moonstone-dropdown').click();
        cy.get('.moonstone-menu').within(() => {
            cy.contains('Users').should('be.visible');
            // The groups come after the users, below the visible part of the menu
            cy.contains('Groups').should('exist');
            cy.contains('li.moonstone-menuItem', `site-privileged (site ${SITE})`).should('exist');
            cy.contains('li.moonstone-menuItem', new RegExp(`^${USER}$`)).click();
        });
        getDetailsPanel().within(() => {
            cy.contains('button', /^Fix$/).click();
            cy.contains('Fixed').should('be.visible');
        });
        // The fix status, in the last column, is displayed once the panel is closed
        closeDetailsPanel();
        getRow(NO_PRINCIPAL).within(() => cy.contains('Fixed').should('be.visible'));
    });
});
