import {configureCheck, graphql, resetCheckConfiguration} from '../../support/integrity';

const CHECKS = [
    'AceSanityCheck', 'BinaryPropertiesSanityCheck', 'ChildNodeDefinitionsSanityCheck', 'FlatStorageCheck', 'HomePageDeclarationCheck',
    'JCRLanguagePropertyCheck', 'LivePropertiesCheck', 'LockSanityCheck', 'MarkForDeletionCheck', 'NodeNameInfoSanityCheck', 'PagesSanityCheck',
    'PropertyDefinitionsSanityCheck', 'PublicationSanityDefaultCheck', 'PublicationSanityLiveCheck', 'ReferencesSanityCheck',
    'SiteLevelSystemGroupsCheck', 'StaticInternalLinksCheck', 'TemplatesIndexationCheck', 'UndeclaredNodeTypesCheck',
    'UndeployedModulesReferencesCheck', 'UnreadablePublicationStatusCheck', 'UserAccountSanityCheck', 'VersionHistoryCheck',
    'VersionSanityCheck', 'WipSanityCheck', 'WorkspaceSpecificDefinitionsCheck'
];
const DISABLED_BY_DEFAULT = ['LivePropertiesCheck', 'NodeNameInfoSanityCheck', 'StaticInternalLinksCheck', 'VersionHistoryCheck', 'VersionSanityCheck'];

const readConfiguration = (checkId: string, name: string): Cypress.Chainable<any> =>
    graphql('query($id: String, $name: String!) { integrity: contentIntegrity { check: integrityCheckById(id: $id) { configuration(name: $name) { name value defaultValue type } } } }',
        {id: checkId, name})
        .then(data => data.integrity.check.configuration);

describe('Integrity checks', () => {
    let checks: { id: string; enabled: boolean; configurable: boolean }[];

    before(() => {
        graphql('{ integrity: contentIntegrity { checks: integrityChecks { id enabled configurable } } }').then(data => {
            checks = data.integrity.checks;
        });
    });

    it('registers every check', () => {
        expect(checks.map(c => c.id)).to.include.members(CHECKS);
    });

    it('disables some checks by default', () => {
        expect(checks.filter(c => !c.enabled).map(c => c.id).sort()).to.deep.equal(DISABLED_BY_DEFAULT);
    });

    it('tells which checks are configurable', () => {
        ['AceSanityCheck', 'BinaryPropertiesSanityCheck', 'FlatStorageCheck', 'PropertyDefinitionsSanityCheck', 'PublicationSanityLiveCheck',
            'ReferencesSanityCheck', 'StaticInternalLinksCheck', 'VersionHistoryCheck', 'WorkspaceSpecificDefinitionsCheck'].forEach(id => {
            expect(checks.find(c => c.id === id).configurable, `${id} is configurable`).to.be.true;
        });
        expect(checks.find(c => c.id === 'LockSanityCheck').configurable).to.be.false;
    });

    describe('Configuration', () => {
        after(() => resetCheckConfiguration('FlatStorageCheck'));

        it('changes a configuration, then resets it to its default value', () => {
            configureCheck('FlatStorageCheck', 'threshold', '42');
            readConfiguration('FlatStorageCheck', 'threshold').then(conf => {
                expect(conf.value).to.equal('42');
                expect(conf.defaultValue).to.equal('500');
            });
            resetCheckConfiguration('FlatStorageCheck');
            readConfiguration('FlatStorageCheck', 'threshold').then(conf => expect(conf.value).to.equal('500'));
        });

        it('refuses a value which does not match the type of the configuration', () => {
            graphql('{ integrity: contentIntegrity { check: integrityCheckById(id: "FlatStorageCheck") { configure(name: "threshold", value: "not a number") } } }')
                .then(data => expect(data.integrity.check.configure).to.be.false);
            readConfiguration('FlatStorageCheck', 'threshold').then(conf => expect(conf.value).to.equal('500'));
        });
    });
});
