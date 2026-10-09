import {
    createTestSite,
    deleteTestSite,
    expectError,
    expectExtraInfo,
    expectFixFails,
    expectNoFix,
    fixAndVerify,
    fixError,
    graphql,
    runFixture,
    scan,
    ScanResults
} from '../../support/integrity';

const SITE = 'ciPropertyDefinitionsCheck';
const ROOT = `/sites/${SITE}/contents/property-definitions`;
const MOUNT_POINT = '/mounts/ci-tests-validation';
const CHECKS = ['PropertyDefinitionsSanityCheck'];
const MISSING_MANDATORY = `${ROOT}/missing-mandatory`;
const INVALID_VALUES = `${ROOT}/invalid-values`;
// ciTestChoices holds first, not-a-choice and second, and only accepts first and second
const INVALID_CHOICES = `${ROOT}/invalid-choices`;

const readFixValues = (resultsId: string, errorId: string): Cypress.Chainable<Record<string, unknown>> =>
    graphql('query($r: String, $id: String!) { integrity: contentIntegrity { results: scanResultsDetails(id: $r) { error: errorById(id: $id) { fixValues { name type multiple choices defaultValues description } } } } }',
        {r: resultsId, id: errorId}).then(data => data.integrity.results.error.fixValues);

describe('PropertyDefinitionsSanityCheck', () => {
    let results: ScanResults;

    before(() => {
        createTestSite(SITE);
        runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE});
        scan(ROOT, CHECKS).then(r => {
            results = r;
        });
    });

    after(() => {
        runFixture('checks/PropertyDefinitionsSanityCheck-cleanup.groovy', {SITEKEY: SITE});
        deleteTestSite(SITE);
    });

    describe('Detection', () => {
        it('detects EMPTY_MANDATORY_PROPERTY on a node without its mandatory property', () => {
            const error = expectError(results, 'EMPTY_MANDATORY_PROPERTY', MISSING_MANDATORY);
            expectExtraInfo(error, 'property-name', 'width');
            expectExtraInfo(error, 'declaring-type', 'jnt:frame');
        });

        it('detects INVALID_VALUE_TYPE on a value whose type is not the type of the definition', () => {
            const error = expectError(results, 'INVALID_VALUE_TYPE', INVALID_VALUES);
            expectExtraInfo(error, 'property-name', 'ciTestNumber');
            expectExtraInfo(error, 'value-type', 'String');
            expectExtraInfo(error, 'expected-value-type', 'Long');
        });

        it('detects INVALID_MULTI_VALUE_STATUS on a single value for a multiple property', () => {
            expectExtraInfo(expectError(results, 'INVALID_MULTI_VALUE_STATUS', INVALID_VALUES), 'property-name', 'ciTestList');
        });

        it('detects INVALID_VALUE_CONSTRAINT on a value out of the choice list', () => {
            const error = expectError(results, 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES);
            expectExtraInfo(error, 'property-name', 'ciTestChoice');
            expectExtraInfo(error, 'invalid-value', 'not-a-choice');
        });

        it('detects UNDECLARED_PROPERTY on a property which is not declared anymore', () => {
            expectExtraInfo(expectError(results, 'UNDECLARED_PROPERTY', INVALID_VALUES), 'property-name', 'ciTestRemoved');
        });

        it('detects INVALID_NODE_VALIDATION on a node refused by its validator', () => {
            scan(MOUNT_POINT, CHECKS).then(r => {
                expectExtraInfo(expectError(r, 'INVALID_NODE_VALIDATION', MOUNT_POINT), 'property-name', 'rootPath');
            });
        });
    });

    describe('Fix', () => {
        it('fixes UNDECLARED_PROPERTY by removing the property', () => {
            fixAndVerify(ROOT, CHECKS, 'EDIT', 'UNDECLARED_PROPERTY', INVALID_VALUES);
        });

        ['INVALID_VALUE_TYPE', 'INVALID_MULTI_VALUE_STATUS'].forEach(errorType => {
            it(`provides no fix for ${errorType}, whose right value can't be guessed`, () => {
                expectNoFix(ROOT, CHECKS, 'EDIT', errorType, INVALID_VALUES);
            });
        });

        it('does not fix INVALID_VALUE_CONSTRAINT without value, when the definition has no default value', () => {
            expectFixFails(ROOT, CHECKS, 'EDIT', 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES);
        });

        it('provides no fix for INVALID_NODE_VALIDATION', () => {
            expectNoFix(MOUNT_POINT, CHECKS, 'EDIT', 'INVALID_NODE_VALIDATION', MOUNT_POINT);
        });

        it('does not fix EMPTY_MANDATORY_PROPERTY without value, when the definition has no default value', () => {
            expectFixFails(ROOT, CHECKS, 'EDIT', 'EMPTY_MANDATORY_PROPERTY', MISSING_MANDATORY);
        });

        it('describes the value to type to fix EMPTY_MANDATORY_PROPERTY', () => {
            scan(ROOT, CHECKS).then(r => {
                const error = expectError(r, 'EMPTY_MANDATORY_PROPERTY', MISSING_MANDATORY);
                expect(error.fixWithValues).to.be.true;
                cy.request({
                    method: 'POST',
                    url: '/modules/graphql',
                    auth: {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD')},
                    headers: {Origin: Cypress.config().baseUrl},
                    body: {
                        query: 'query($r: String, $id: String) { integrity: contentIntegrity { results: scanResultsDetails(id: $r) { error: errorById(id: $id) { fixValues { name type multiple choices defaultValues } } } } }',
                        variables: {r: r.resultsId, id: error.id}
                    }
                }).then(response => {
                    expect(response.body.data.integrity.results.error.fixValues).to.deep.equal({
                        name: 'width', type: 'Long', multiple: false, choices: [], defaultValues: []
                    });
                });
            });
        });

        it('rejects a value which does not match the type of the property', () => {
            scan(ROOT, CHECKS).then(r => {
                const error = expectError(r, 'EMPTY_MANDATORY_PROPERTY', MISSING_MANDATORY);
                fixError(r.resultsId, error.id, ['not a number']).then(result => {
                    expect(result.fixed).to.be.false;
                    expect(result.message).to.contain('not a number');
                });
                fixError(r.resultsId, error.id, ['  ']).then(result => expect(result.message).to.contain('A value is required'));
                fixError(r.resultsId, error.id, ['1', '2']).then(result => expect(result.message).to.contain('takes a single value'));
            });
        });

        it('fixes EMPTY_MANDATORY_PROPERTY with a typed value', () => {
            scan(ROOT, CHECKS).then(r => {
                const error = expectError(r, 'EMPTY_MANDATORY_PROPERTY', MISSING_MANDATORY);
                fixError(r.resultsId, error.id, ['640']).then(result => expect(result.fixed, result.message).to.be.true);
            });
            scan(ROOT, CHECKS).then(r => expect(r.errors.filter(e => e.errorType === 'EMPTY_MANDATORY_PROPERTY' && e.nodePath === MISSING_MANDATORY)).to.have.length(0));
        });

        describe('INVALID_VALUE_CONSTRAINT with a chosen value', () => {
            beforeEach(() => runFixture('checks/PropertyDefinitionsSanityCheck.groovy', {SITEKEY: SITE}));

            it('offers the values accepted by the constraints of the definition', () => {
                scan(ROOT, CHECKS).then(r => {
                    const error = expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES);
                    expect(error.fixWithValues).to.be.true;
                    readFixValues(r.resultsId, error.id).then(fixValues => {
                        expect(fixValues).to.deep.include({name: 'ciTestChoice', type: 'String', multiple: false, choices: ['first', 'second'], defaultValues: []});
                        expect(fixValues.description).to.contain('The value not-a-choice of the property ciTestChoice does not match');
                    });
                });
            });

            it('keeps the other values of a multiple property, and leaves the invalid one to choose', () => {
                scan(ROOT, CHECKS).then(r => {
                    const error = expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_CHOICES);
                    readFixValues(r.resultsId, error.id).then(fixValues => {
                        expect(fixValues).to.deep.include({name: 'ciTestChoices', multiple: true, choices: ['first', 'second'], defaultValues: ['first', '', 'second']});
                    });
                });
            });

            it('rejects a value which does not match the constraints', () => {
                scan(ROOT, CHECKS).then(r => {
                    fixError(r.resultsId, expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES).id, ['third']).then(result => {
                        expect(result.fixed).to.be.false;
                        expect(result.message).to.contain('The value third does not match the constraints');
                    });
                });
                scan(ROOT, CHECKS).then(r => expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES));
            });

            it('fixes INVALID_VALUE_CONSTRAINT with a chosen value', () => {
                scan(ROOT, CHECKS).then(r => {
                    fixError(r.resultsId, expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_VALUES).id, ['second'])
                        .then(result => expect(result.fixed, result.message).to.be.true);
                    fixError(r.resultsId, expectError(r, 'INVALID_VALUE_CONSTRAINT', INVALID_CHOICES).id, ['first', 'second', 'second'])
                        .then(result => expect(result.fixed, result.message).to.be.true);
                });
                scan(ROOT, CHECKS).then(r => expect(r.errors.filter(e => e.errorType === 'INVALID_VALUE_CONSTRAINT')).to.have.length(0));
                graphql(`{ jcr { a: nodeByPath(path: "${INVALID_VALUES}") { property(name: "ciTestChoice") { value } } b: nodeByPath(path: "${INVALID_CHOICES}") { property(name: "ciTestChoices") { values } } } }`)
                    .then(data => {
                        expect(data.jcr.a.property.value).to.equal('second');
                        expect(data.jcr.b.property.values).to.deep.equal(['first', 'second', 'second']);
                    });
            });
        });
    });
});
