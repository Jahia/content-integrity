import {
    configureCheck,
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectNoError,
    fixAndVerify,
    fixFromTwoScans,
    resetCheckConfiguration,
    runFixture,
    scan
} from '../../support/integrity';

const SITE = 'ciWorkspaceDefinitionsCheck';
const ROOT = `/sites/${SITE}/contents/workspace-specific-definitions`;
const CHECK = 'WorkspaceSpecificDefinitionsCheck';
const CHECKS = [CHECK];

describe('WorkspaceSpecificDefinitionsCheck', () => {
    before(() => {
        createTestSite(SITE);
        runFixture('checks/WorkspaceSpecificDefinitionsCheck.groovy', {SITEKEY: SITE});
    });

    after(() => {
        resetCheckConfiguration(CHECK);
        deleteTestSite(SITE);
    });

    describe('With the default configuration', () => {
        it('detects UNEXPECTED_TYPE on a live node flagged with jmix:markedForDeletion', () => {
            scan(ROOT, CHECKS, 'LIVE').then(results => {
                expectExtraInfo(expectError(results, 'UNEXPECTED_TYPE', `${ROOT}/unexpected-type`), 'unexpected-type', 'jmix:markedForDeletion');
            });
        });

        it('detects UNEXPECTED_PROP_VALUE on a live node with a WIP status other than DISABLED', () => {
            scan(ROOT, CHECKS, 'LIVE').then(results => {
                const error = expectError(results, 'UNEXPECTED_PROP_VALUE', `${ROOT}/unexpected-property-value`);
                expectExtraInfo(error, 'unexpected-prop', 'j:workInProgressStatus');
                expectExtraInfo(error, 'unexpected-prop-value', 'ALL_CONTENT');
            });
        });

        it('does not report j:liveProperties in default', () => {
            scan(ROOT, CHECKS).then(results => expectNoError(results, 'UNEXPECTED_PROP', `${ROOT}/unexpected-property`));
        });

        it('fixes UNEXPECTED_TYPE', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'UNEXPECTED_TYPE', `${ROOT}/unexpected-type`);
        });

        it('fixes UNEXPECTED_PROP_VALUE', () => {
            fixAndVerify(ROOT, CHECKS, 'LIVE', 'UNEXPECTED_PROP_VALUE', `${ROOT}/unexpected-property-value`);
        });
    });

    describe('With j:liveProperties configured as a live only property', () => {
        before(() => configureCheck(CHECK, 'ws-live-specific-props', 'j:liveProperties'));

        after(() => resetCheckConfiguration(CHECK));

        it('detects UNEXPECTED_PROP on a node of the default workspace with j:liveProperties', () => {
            scan(ROOT, CHECKS).then(results => {
                expectExtraInfo(expectError(results, 'UNEXPECTED_PROP', `${ROOT}/unexpected-property`), 'unexpected-prop', 'j:liveProperties');
            });
        });

        it('fixes UNEXPECTED_PROP, and reports it fixed from other results', () => {
            // Fixed from the first results, then from the second ones, where it is already fixed
            fixFromTwoScans(ROOT, CHECKS, 'EDIT', 'UNEXPECTED_PROP', `${ROOT}/unexpected-property`);
        });
    });
});
