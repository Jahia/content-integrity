import {runFixture, scan} from '../../support/integrity';
import {getDetailsPanel, getRow, openErrorDetails, visitAdmin} from '../../support/adminPage';

const MOUNT = 'ciUiVirtualNodes';
const EMPTY_FILE = `/mounts/${MOUNT}/empty.txt/jcr:content`;

describe('Errors of virtual nodes in the administration page', () => {
    before(() => {
        runFixture('virtualNodes/vfsMount.groovy', {MOUNT_NAME: MOUNT});
        // The page displays the latest results
        scan(`/mounts/${MOUNT}`, ['BinaryPropertiesSanityCheck']);
    });

    after(() => runFixture('virtualNodes/vfsMount-cleanup.groovy', {MOUNT_NAME: MOUNT}));

    it('offers no fix for the error of a virtual node, and tells why', () => {
        visitAdmin();
        openErrorDetails(EMPTY_FILE);
        getRow(EMPTY_FILE).find('.moonstone-chip').should('not.exist');
        getDetailsPanel().within(() => {
            cy.contains('The node is virtual, served by an external provider such as a mount point: its errors are not fixed here.').should('be.visible');
            cy.contains('button', /^Fix/).should('not.exist');
        });
    });
});
