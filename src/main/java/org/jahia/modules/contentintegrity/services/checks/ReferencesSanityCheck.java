package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.api.JCRUtils;
import org.jahia.modules.contentintegrity.services.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jahia.modules.contentintegrity.api.Constants.CALCULATION_ERROR;
import static org.jahia.modules.contentintegrity.api.Constants.JAHIAMIX_REFERENCES_IN_FIELD;
import static org.jahia.modules.contentintegrity.api.Constants.JAHIANT_REFERENCEINFIELD_FIELDNAME;
import static org.jahia.modules.contentintegrity.api.Constants.JAHIANT_REFERENCEINFIELD_LANG_SEP;
import static org.jahia.modules.contentintegrity.api.Constants.JAHIANT_TRANSLATION;
import static org.jahia.modules.contentintegrity.api.Constants.JAHIA_REFERENCE_IN_FIELD_PREFIX;
import static org.jahia.modules.contentintegrity.api.Constants.MIX_VERSIONABLE;
import static org.jahia.modules.contentintegrity.services.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class ReferencesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable, ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final String VALIDATE_REFS = "validate-refs";
    private static final String VALIDATE_BACK_REFS = "validate-back-refs";
    private static final String VALIDATE_VERSION_HISTORY = "validate-version-history";
    public static final ContentIntegrityErrorType INVALID_BACK_REF = createErrorType("INVALID_BACK_REF", "Missing referencing node");
    public static final ContentIntegrityErrorType BROKEN_REF = createErrorType("BROKEN_REF", "Broken reference");
    public static final ContentIntegrityErrorType BROKEN_REF_TO_VN = createErrorType("BROKEN_REF_TO_VN", "Broken reference to a virtual node");
    public static final ContentIntegrityErrorType BROKEN_REF_IN_FIELD = createErrorType("BROKEN_REF_IN_FIELD", "Link of a text without a referenced node");

    private static final String REF_IN_FIELD_PLACEHOLDER_PREFIX = "##ref:link";
    // Same placeholder as the one resolved by the URLInterceptor when a text is read
    private static final Pattern REF_IN_FIELD_PLACEHOLDER = Pattern.compile("##ref:link([0-9]+)##");
    private static final String J_REFERENCE = "j:reference";
    // The HTML elements without content nor end tag: such an element whose link is broken is removed, the other ones are unwrapped
    private static final Pattern TAG_NAME = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\b");
    private static final Collection<String> VOID_ELEMENTS =Arrays.asList("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr");

    // A reference to a virtual node, and an invalid back reference, are not fixed
    private static final Collection<ContentIntegrityErrorType> FIXABLE_ERRORS = Arrays.asList(BROKEN_REF, BROKEN_REF_IN_FIELD);

    private final ContentIntegrityCheckConfiguration configurations;

    public ReferencesSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(VALIDATE_REFS, Boolean.TRUE, BOOLEAN_PARSER, "Check the references sanity");
        configurations.declareDefaultParameter(VALIDATE_BACK_REFS, Boolean.FALSE, BOOLEAN_PARSER, "Check the back references sanity");
        configurations.declareDefaultParameter(VALIDATE_VERSION_HISTORY, Boolean.FALSE, BOOLEAN_PARSER, "Check the version history");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return Utils.mergeErrorLists(
                checkBackReferences(node),
                checkReferences(node)
        );
    }

    private ContentIntegrityErrorList checkReferences(JCRNodeWrapper node) {
        if (!((Boolean) getConfigurations().getParameter(VALIDATE_REFS))) return null;

        // Read from the real node, to bypass the interceptors: the URLInterceptor replaces each broken ##ref:linkN## of a
        // text by #, which hides BROKEN_REF_IN_FIELD, and logs a warning for it
        final PropertyIterator properties = JCRUtils.runJcrCallBack(node.getRealNode(), Node::getProperties);
        if (properties == null) return null;

        ContentIntegrityErrorList errors = null;

        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            final PropertyDefinition definition;
            try {
                definition = property.getDefinition();
                property.getType();
            } catch (RepositoryException e) {
                if (errors == null) errors = createEmptyErrorsList();
                errors.addError(createFrameworkError(node, String.format("Skipping %s as its definition is inconsistent", JCRUtils.runJcrCallBack(property, Item::getPath, CALCULATION_ERROR)), e));
                continue;
            }
            if (!((Boolean) getConfigurations().getParameter(VALIDATE_VERSION_HISTORY)) && StringUtils.equals(definition.getDeclaringNodeType().getName(), MIX_VERSIONABLE)) {
                continue;
            }

            final Boolean isMultiple = JCRUtils.runJcrCallBack(property, Property::isMultiple);
            if (isMultiple == null) continue;

            final ContentIntegrityErrorList propErrors;
            if (isMultiple) {
                final Value[] values = JCRUtils.runJcrCallBack(property, Property::getValues);
                if (values == null) continue;
                propErrors = Utils.mergeErrorLists(Arrays.stream(values)
                        .map(v -> checkPropertyValue(v, node, property))
                        .filter(Objects::nonNull)
                        .toArray(ContentIntegrityErrorList[]::new));
            } else {
                final Value value = JCRUtils.runJcrCallBack(property, Property::getValue);
                if (value == null) continue;
                propErrors = checkPropertyValue(value, node, property);
            }
            errors = Utils.mergeErrorLists(errors, propErrors);
        }
        return errors;
    }

    private ContentIntegrityErrorList checkPropertyValue(Value value, JCRNodeWrapper checkedNode, Property property) {
        switch (value.getType()) {
            case PropertyType.STRING:
                return checkReferencesInField(value, checkedNode, property);
            case PropertyType.REFERENCE:
            case PropertyType.WEAKREFERENCE:
                return JCRUtils.runJcrSupplierCallBack(() -> {
                    final String uuid = value.getString();
                    if (JCRUtils.nodeExists(uuid, checkedNode.getSession())) return null;
                    if (JCRUtils.isVirtualNodeIdentifier(uuid)) {
                        return createSingleError(createPropertyRelatedError(checkedNode, BROKEN_REF_TO_VN)
                                .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Property::getName, CALCULATION_ERROR))
                                .addExtraInfo("missing-uuid", uuid, true));
                    }
                    return createSingleError(createPropertyRelatedError(checkedNode, BROKEN_REF)
                            .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Property::getName, CALCULATION_ERROR))
                            .addExtraInfo("missing-uuid", uuid, true));
                });
            default:
                return null;
        }
    }

    /*
     * The links of a text to a node are stored as ##ref:linkN## placeholders, where N is the index of the
     * jnt:referenceInField child node (named j:referenceInField_<fieldName>_N) holding the identifier of the linked node.
     * A placeholder without such a child node, or whose child node has no j:reference, can't be resolved when the text
     * is rendered. A j:reference to a missing node is reported as BROKEN_REF on the jnt:referenceInField node.
     */
    private ContentIntegrityErrorList checkReferencesInField(Value value, JCRNodeWrapper checkedNode, Property property) {
        return JCRUtils.runJcrSupplierCallBack(() -> {
            final String text = value.getString();
            if (!StringUtils.contains(text, REF_IN_FIELD_PLACEHOLDER_PREFIX)) return null;

            final Node textNode = checkedNode.getRealNode();
            final String propertyName = property.getName();
            final Set<Long> references = getReferencesInField(getReferencesNode(textNode), getFieldName(textNode, propertyName));

            ContentIntegrityErrorList errors = null;
            final Set<Long> reported = new HashSet<>();
            final Matcher matcher = REF_IN_FIELD_PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                final Long index = parseReferenceIndex(matcher.group(1));
                if (index == null || references.contains(index) || !reported.add(index)) continue;
                errors = trackError(errors, createPropertyRelatedError(checkedNode, BROKEN_REF_IN_FIELD)
                        .addExtraInfo("property-name", propertyName)
                        .addExtraInfo("placeholder", matcher.group(), true));
            }
            return errors;
        });
    }

    /*
     * The jnt:referenceInField nodes of a text are children of the node holding the text, or of the node a translation
     * node belongs to. Their j:fieldName is the name of the property, followed by the language for a translation node.
     */
    private Node getReferencesNode(Node textNode) throws RepositoryException {
        return textNode.isNodeType(JAHIANT_TRANSLATION) ? textNode.getParent() : textNode;
    }

    private String getFieldName(Node textNode, String propertyName) throws RepositoryException {
        return textNode.isNodeType(JAHIANT_TRANSLATION) ?
                propertyName + JAHIANT_REFERENCEINFIELD_LANG_SEP + JCRUtils.getTranslationNodeLocale(textNode) : propertyName;
    }

    private Set<Long> getReferencesInField(Node node, String fieldName) throws RepositoryException {
        final Set<Long> references = new HashSet<>();
        if (!node.isNodeType(JAHIAMIX_REFERENCES_IN_FIELD)) return references;

        final NodeIterator referenceNodes = node.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
        while (referenceNodes.hasNext()) {
            final Node referenceNode = referenceNodes.nextNode();
            if (!referenceNode.hasProperty(J_REFERENCE) || !referenceNode.hasProperty(JAHIANT_REFERENCEINFIELD_FIELDNAME)) continue;
            if (!StringUtils.equals(fieldName, referenceNode.getProperty(JAHIANT_REFERENCEINFIELD_FIELDNAME).getString())) continue;
            final Long index = parseReferenceIndex(StringUtils.substringAfterLast(referenceNode.getName(), JAHIANT_REFERENCEINFIELD_LANG_SEP));
            if (index != null) references.add(index);
        }
        return references;
    }

    private Long parseReferenceIndex(String index) {
        try {
            return Long.valueOf(index);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ContentIntegrityErrorList checkBackReferences(JCRNodeWrapper node) {
        if (!((Boolean) getConfigurations().getParameter(VALIDATE_BACK_REFS))) return null;

        final PropertyIterator weakReferences = JCRUtils.isExternalNode(node) ? null : JCRUtils.runJcrCallBack(node, Node::getWeakReferences);
        final PropertyIterator references = JCRUtils.isExternalNode(node) ? null : JCRUtils.runJcrCallBack(node, Node::getReferences);
        return Utils.mergeErrorLists(
                checkBackReferences(weakReferences, node),
                checkBackReferences(references, node)
        );
    }

    private ContentIntegrityErrorList checkBackReferences(PropertyIterator propertyIterator, JCRNodeWrapper checkedNode) {
        if (propertyIterator == null) return null;

        ContentIntegrityErrorList errors = null;
        while (propertyIterator.hasNext()) {
            final Property property = propertyIterator.nextProperty();
            final String referencingNodeID;
            try {
                referencingNodeID = property.getParent().getIdentifier();
                property.getSession().getNodeByIdentifier(referencingNodeID);
            } catch (RepositoryException e) {
                if (errors == null) errors = createEmptyErrorsList();
                errors.addError(createError(checkedNode, INVALID_BACK_REF)
                        .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Item::getName, CALCULATION_ERROR))
                        .addExtraInfo("referencing-node-path", JCRUtils.runJcrSupplierCallBack(() -> property.getParent().getPath(), CALCULATION_ERROR), true));
            }
        }
        return errors;
    }

    @Override
    public boolean isFixable(ContentIntegrityError error) {
        return FIXABLE_ERRORS.contains(error.getErrorType());
    }

    /*
     * From the jcr-scripts fix: the broken reference is removed. On a multi-valued property, only the broken
     * value is removed, and the property is removed when no value remains. A broken reference to a virtual
     * node is not fixed, since the provider might be temporarily unavailable.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (error.getErrorType().equals(BROKEN_REF_IN_FIELD)) return fixReferenceInField(node, error);
        if (!error.getErrorType().equals(BROKEN_REF)) return false;
        final String propertyName = (String) error.getExtraInfo("property-name");
        final String missingUuid = (String) error.getExtraInfo("missing-uuid");
        final JCRNodeWrapper target = RepairUtils.getErrorTarget(node, error);
        if (StringUtils.isBlank(propertyName)) return false;
        // A property already removed, for example by the fix of another error, is fixed
        if (!target.hasProperty(propertyName)) return true;

        RepairUtils.runWithListenersDisabled(() -> {
            final JCRPropertyWrapper property = target.getProperty(propertyName);
            if (property.isMultiple() && missingUuid != null) {
                final List<Value> kept = new ArrayList<>();
                for (JCRValueWrapper value : property.getValues()) {
                    if (!missingUuid.equals(value.getString())) kept.add(value);
                }
                if (!kept.isEmpty()) {
                    property.setValue(kept.toArray(new Value[0]));
                    target.saveSession();
                    return;
                }
            }
            property.remove();
            target.saveSession();
        });
        return true;
    }

    /*
     * Removes the broken link from the text, as when it is removed in CKEditor: the element holding the placeholder is
     * unwrapped, its content being kept (the text of a link), or removed when it has no content (an image). The
     * jnt:referenceInField node of the link, which has no j:reference, is deleted. The other links of the text keep
     * their index. The text and the node are updated at the Jackrabbit level, to keep the stored placeholders.
     */
    private boolean fixReferenceInField(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final String propertyName = (String) error.getExtraInfo("property-name");
        final Long index = parseReferenceIndex(StringUtils.substringBetween((String) error.getExtraInfo("placeholder"), REF_IN_FIELD_PLACEHOLDER_PREFIX, "##"));
        if (StringUtils.isBlank(propertyName) || index == null) return false;

        final Node textNode = RepairUtils.getErrorTarget(node, error).getRealNode();
        final Node referencesNode = getReferencesNode(textNode);
        final String fieldName = getFieldName(textNode, propertyName);
        // A link whose node has been referenced again since the scan is not broken anymore
        if (getReferencesInField(referencesNode, fieldName).contains(index)) return true;

        // A placeholder outside of the attributes of a tag, in the text itself, is not a link which can be removed: the
        // text is left unchanged, and the error is not fixed
        final String placeholder = REF_IN_FIELD_PLACEHOLDER_PREFIX + index + "##";
        final Property property = textNode.hasProperty(propertyName) ? textNode.getProperty(propertyName) : null;
        final String[] fixed = property == null ? new String[0] : property.isMultiple() ?
                Arrays.stream(property.getValues()).map(v -> JCRUtils.runJcrSupplierCallBack(v::getString)).map(v -> removeLink(v, index)).toArray(String[]::new) :
                new String[]{removeLink(property.getString(), index)};
        if (Arrays.stream(fixed).anyMatch(value -> StringUtils.contains(value, placeholder))) return false;

        RepairUtils.runWithListenersDisabled(() -> {
            if (property != null) {
                if (property.isMultiple()) {
                    property.setValue(fixed);
                } else {
                    property.setValue(fixed[0]);
                }
            }
            final NodeIterator referenceNodes = referencesNode.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
            while (referenceNodes.hasNext()) {
                final Node referenceNode = referenceNodes.nextNode();
                if (referenceNode.hasProperty(JAHIANT_REFERENCEINFIELD_FIELDNAME)
                        && StringUtils.equals(fieldName, referenceNode.getProperty(JAHIANT_REFERENCEINFIELD_FIELDNAME).getString())
                        && index.equals(parseReferenceIndex(StringUtils.substringAfterLast(referenceNode.getName(), JAHIANT_REFERENCEINFIELD_LANG_SEP)))) {
                    referenceNode.remove();
                }
            }
            textNode.getSession().save();
        });
        node.getSession().refresh(false);
        return true;
    }

    /*
     * Removes from the HTML the elements whose attributes hold the placeholder of the link: the start tag and its end
     * tag, keeping the content, or the whole element when it has no content.
     * The value is written by the editors: the tags are found without a pattern which backtracks from each '<', since on a
     * value made of many unclosed tags, its time would grow with the square of the length of the value.
     */
    static String removeLink(String html, long index) {
        final String placeholder = REF_IN_FIELD_PLACEHOLDER_PREFIX + index + "##";
        String result = html;
        int[] startTag;
        while ((startTag = findStartTag(result, placeholder)) != null) {
            final int start = startTag[0];
            final int contentStart = startTag[1];
            final String tagName = result.substring(start + 1, startTag[2]).toLowerCase();
            if (VOID_ELEMENTS.contains(tagName) || result.charAt(contentStart - 2) == '/') {
                result = result.substring(0, start) + result.substring(contentStart);
            } else {
                // The end tag of the element, skipping the nested elements with the same name. The search stops at the
                // last '>', so that each candidate tag runs up to the next '>' at most, without backtracking.
                final Matcher tags = Pattern.compile("<(/?)" + Pattern.quote(tagName) + "\\b[^>]*+>", Pattern.CASE_INSENSITIVE).matcher(result);
                tags.region(contentStart, result.lastIndexOf('>') + 1);
                int depth = 1;
                int endStart = -1;
                int endEnd = -1;
                while (tags.find()) {
                    depth += tags.group(1).isEmpty() ? 1 : -1;
                    if (depth == 0) {
                        endStart = tags.start();
                        endEnd = tags.end();
                        break;
                    }
                }
                result = endStart < 0 ?
                        result.substring(0, start) + result.substring(contentStart) :
                        result.substring(0, start) + result.substring(contentStart, endStart) + result.substring(endEnd);
            }
        }
        return result;
    }

    /*
     * The first start tag whose attributes hold the placeholder: a '<' followed by a tag name, then no '>' up to the
     * placeholder, then the first '>' after it. Returns its start, its end and the end of its name, or null. The value is
     * read once, from left to right.
     */
    private static int[] findStartTag(String html, String placeholder) {
        final Matcher tagName = TAG_NAME.matcher(html).useTransparentBounds(true);
        int tagStart = -1;
        int nameEnd = -1;
        for (int i = 0; i < html.length(); i++) {
            final char c = html.charAt(i);
            if (c == '>') {
                tagStart = -1;
            } else if (c == '<') {
                if (tagStart < 0 && tagName.region(i, html.length()).lookingAt()) {
                    tagStart = i;
                    nameEnd = tagName.end();
                    i = nameEnd - 1;
                }
            } else if (tagStart >= 0 && html.startsWith(placeholder, i)) {
                final int tagEnd = html.indexOf('>', i + placeholder.length());
                return tagEnd < 0 ? null : new int[]{tagStart, tagEnd + 1, nameEnd};
            }
        }
        return null;
    }
}
