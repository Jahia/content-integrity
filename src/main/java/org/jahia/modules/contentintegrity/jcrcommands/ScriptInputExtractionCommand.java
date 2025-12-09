package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.api.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(scope = "jcr", name = "integrity-extract-txt", description = "Generate the input txt files for the scripts")
@Service
public class ScriptInputExtractionCommand implements Action {
    private static final Logger logger = LoggerFactory.getLogger(ScriptInputExtractionCommand.class);

    // content-integrity < 3.29 "Check ID";"Fixed";"Error type";"Workspace";"Node identifier";"Node path";"Site";"Node primary type";"Node mixins";"Locale";"Error message";"Extra information";"Specific extra information"
    // content-integrity >= 3.29 "Check ID";"Fixed";"Error type";"Impact on XML import";"Workspace";"Node identifier";"Node path";"Site";"Node primary type";"Node mixins";"Locale";"Error message";"Extra information";"Specific extra information"
    private static final int HEADER_CHECK_ID = 0;
    private static final int HEADER_ERROR_TYPE = 2;
    // private static final int HEADER_WORKSPACE = 3;
    private static final int HEADER_WORKSPACE = 4;
    // private static final int HEADER_NODE_IDENTIFIER = 4;
    private static final int HEADER_NODE_IDENTIFIER = 5;
    // private static final int HEADER_LOCALE = 9;
    private static final int HEADER_LOCALE = 10;
    // private static final int HEADER_ERROR_MESSAGE = 10;
    private static final int HEADER_ERROR_MESSAGE = 11;
    // private static final int HEADER_EXTRA_INFORMATION = 11;
    private static final int HEADER_EXTRA_INFORMATION = 12;
    // private static final int HEADER_SPECIFIC_EXTRA_INFORMATION = 12;
    private static final int HEADER_SPECIFIC_EXTRA_INFORMATION = 13;

    @Option(name = "-o")
    private boolean overrideFiles;

    @Argument(required = true, description = "CSV file or directory containing it")
    private String path;

    @Override
    public Object execute() throws Exception {
        logger.info("<<< Start content-integrity data extraction...");

        final File paramFile = new File(path);
        if (!paramFile.exists()) return endScript();

        final File csvFile;
        final File targetFolder;
        if (paramFile.isDirectory()) {
            targetFolder = paramFile;
            final Collection<File> fileList = FileUtils.listFiles(targetFolder, Collections.singletonList("csv").toArray(new String[0]), false);
            if (fileList.size() != 1) {
                logger.error("Unable to identify the csv file in the specified folder");
                return endScript();
            }
            csvFile = fileList.iterator().next();
        } else {
            csvFile = paramFile;
            targetFolder = paramFile.getParentFile();
        }

        final File valuesFile = new File(targetFolder, "values.txt");
        final Map<String, String> values = new HashMap<>();
        if (valuesFile.exists()) {
            FileUtils.readLines(valuesFile, StandardCharsets.UTF_8).stream()
                    .filter(StringUtils::isNotBlank)
                    .filter(l -> !l.startsWith("#"))
                    .filter(l -> l.contains(";"))
                    .map(l -> l.split(";"))
                    .forEach(l -> values.put(l[0], l[1]));
        }

        final List<String> lines = FileUtils.readLines(csvFile, StandardCharsets.UTF_8);
        //lines.remove(0); // TODO the csv can have no header line

        for (String workspace : new String[]{Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE}) {
            undeployedModulesReferencesCheck(lines, workspace, targetFolder);
            childNodeDefinitionsSanityCheck(lines, workspace, targetFolder);
            undeclaredNodeTypesCheckMixin(lines, workspace, targetFolder);
            undeclaredNodeTypesCheckPrimaryType(lines, workspace, targetFolder);
            propertyDefinitionsSanityCheckEmptyMandatoryProperty(lines, workspace, targetFolder, values);
            propertyDefinitionsSanityCheckInvalidValueConstraint(lines, workspace, targetFolder, values);
            propertyDefinitionsSanityCheckUndeclaredProperty(lines, workspace, targetFolder);
            markForDeletionCheck(lines, workspace, targetFolder);
            publicationSanityLiveCheckMissingDefaultNode(lines, workspace, targetFolder);
            publicationSanityLiveCheckInconsistentUgc(lines, workspace, targetFolder);
            jcrLanguagePropertyCheck(lines, workspace, targetFolder);
            publicationSanityDefaultCheckPathConflict(lines, workspace, targetFolder);

            nodeNameInfoSanityCheck(lines, workspace, targetFolder);
            wipSanityCheck(lines, workspace, targetFolder);
            referencesSanityCheck(lines, workspace, targetFolder);
            binaryPropertiesSanityCheck(lines, workspace, targetFolder);
            publicationSanityDefaultCheckNoLiveNode(lines, workspace, targetFolder);
            publicationSanityDefaultCheckNoLiveNodeAutoPublish(lines, workspace, targetFolder);
            lockSanityCheckDeletionLockOnI18N(lines, workspace, targetFolder);
            lockSanityCheckInconsistentLock(lines, workspace, targetFolder);
        }

        values.clear();
        return endScript();
    }

    private Void endScript() {
        logger.info("<<< ...end content-integrity data extraction.");
        return null;
    }

    private void undeployedModulesReferencesCheck(List<String> lines, String workspace, File targetFolder) {
        /*
        Since the errors are on some autopublished nodes (site), they are reported only for the default workspace.
        But they need to be fixed in both workspace. As a consequence, we do not filter on the workspace here,
        so that the txt file is generated for the live workspace based on the errors tracked for the default workspace
         */
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "UndeployedModulesReferencesCheck".equals(l[HEADER_CHECK_ID]))
                //.filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER] + ";" + extractUndeployedModule(l[HEADER_EXTRA_INFORMATION]))
                .collect(Collectors.toList());
        save(txtLines, "UndeployedModulesReferencesCheck", null, workspace, targetFolder);
    }

    private static String extractUndeployedModule(String s) {
        //  {module=v8-modules-helper}
        return StringUtils.substring(s, "{module=".length(), s.length() - 1);
    }

    private void childNodeDefinitionsSanityCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "ChildNodeDefinitionsSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "ChildNodeDefinitionsSanityCheck", null, workspace, targetFolder);
    }

    private void undeclaredNodeTypesCheckMixin(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "UndeclaredNodeTypesCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "Undeclared mixin type".equals(l[HEADER_ERROR_MESSAGE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER] + ";" + extractUndeclaredMixin(l[HEADER_EXTRA_INFORMATION]))
                .collect(Collectors.toList());
        save(txtLines, "UndeclaredNodeTypesCheck", "MIXIN", workspace, targetFolder);
    }

    private String extractUndeclaredMixin(String s) {
        //  {mixin type=cpg:header}
        return StringUtils.substring(s, "{mixin type=".length(), s.length() - 1);
    }

    private void undeclaredNodeTypesCheckPrimaryType(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "UndeclaredNodeTypesCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "Undeclared primary type".equals(l[HEADER_ERROR_MESSAGE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                //  {primary type=fwk:newsListNewsReference}
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "UndeclaredNodeTypesCheck", "PRIMARY_TYPE", workspace, targetFolder);
    }

    private void propertyDefinitionsSanityCheckEmptyMandatoryProperty(List<String> lines, String workspace, File targetFolder, Map<String, String> values) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "EMPTY_MANDATORY_PROPERTY".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> {
                    final String defaultValue = values.get(l[HEADER_EXTRA_INFORMATION]);
                    if (StringUtils.isBlank(defaultValue)) {
                        logger.warn("No default value for {}", l[HEADER_EXTRA_INFORMATION]);
                        return null;
                    }
                    // {declaring-type=iso:title, property-name=jcr:title}
                    return l[HEADER_NODE_IDENTIFIER] + ";" + l[HEADER_LOCALE] + ";" + extractPropertyName(l[HEADER_EXTRA_INFORMATION]) + ";" + defaultValue;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "EMPTY_MANDATORY_PROPERTY", workspace, targetFolder);
    }

    private static String extractPropertyName(String s) {
        return StringUtils.substring(s, s.indexOf("property-name=") + "property-name=".length(), s.length() - 1);
    }

    private void propertyDefinitionsSanityCheckInvalidValueConstraint(List<String> lines, String workspace, File targetFolder, Map<String, String> values) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "INVALID_VALUE_CONSTRAINT".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> {
                    final String key = l[HEADER_EXTRA_INFORMATION] + l[HEADER_SPECIFIC_EXTRA_INFORMATION];
                    final String defaultValue = values.get(key);
                    if (StringUtils.isBlank(defaultValue)) {
                        logger.warn("No default value for {}", key);
                        return null;
                    }
                    // {constraints=[transparent, light, dark], declaring-type=fwk:allowedInCarousel, property-name=bgcolorText}
                    return l[HEADER_NODE_IDENTIFIER] + ";" + l[HEADER_LOCALE] + ";" + extractPropertyName(l[HEADER_EXTRA_INFORMATION]) + ";" + defaultValue;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "INVALID_VALUE_CONSTRAINT", workspace, targetFolder);
    }

    private void propertyDefinitionsSanityCheckUndeclaredProperty(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "UNDECLARED_PROPERTY".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER] + ";" + l[HEADER_LOCALE] + ";" + extractUndeclaredPropName(l[HEADER_EXTRA_INFORMATION]))
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "UNDECLARED_PROPERTY", workspace, targetFolder);
    }

    private String extractUndeclaredPropName(String s) {
        //  {property-name=j:sceneType}
        return StringUtils.substring(s, "{property-name=".length(), s.length() - 1);
    }

    private void markForDeletionCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "MarkForDeletionCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "MarkForDeletionCheck", null, workspace, targetFolder);
    }

    private void publicationSanityLiveCheckMissingDefaultNode(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PublicationSanityLiveCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "NO_DEFAULT_NODE".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityLiveCheck", "NO_DEFAULT_NODE", workspace, targetFolder);
    }

    private void publicationSanityLiveCheckInconsistentUgc(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PublicationSanityLiveCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "INCONSISTENT_UGC".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityLiveCheck", "INCONSISTENT_UGC", workspace, targetFolder);
    }

    private void jcrLanguagePropertyCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "JCRLanguagePropertyCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "JCRLanguagePropertyCheck", null, workspace, targetFolder);
    }

    private void publicationSanityDefaultCheckPathConflict(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PublicationSanityDefaultCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "PATH_CONFLICT".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityDefaultCheck", "PATH_CONFLICT", workspace, targetFolder);
    }

    private void nodeNameInfoSanityCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "NodeNameInfoSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "NodeNameInfoSanityCheck", null, workspace, targetFolder);
    }

    private void wipSanityCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "WipSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "WipSanityCheck", null, workspace, targetFolder);
    }

    private void referencesSanityCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "ReferencesSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER] + ";" + l[HEADER_LOCALE] + ";" + extractPropertyName(l[HEADER_EXTRA_INFORMATION]))
                .collect(Collectors.toList());
        save(txtLines, "ReferencesSanityCheck", null, workspace, targetFolder);
    }

    private void binaryPropertiesSanityCheck(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "BinaryPropertiesSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "BinaryPropertiesSanityCheck", null, workspace, targetFolder);
    }

    private void publicationSanityDefaultCheckNoLiveNode(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PublicationSanityDefaultCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "NO_LIVE_NODE".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> "Found a node flagged as published, but no corresponding live node exists".equals(l[HEADER_ERROR_MESSAGE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityDefaultCheck", "NO_LIVE_NODE", workspace, targetFolder);
    }

    private void publicationSanityDefaultCheckNoLiveNodeAutoPublish(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "PublicationSanityDefaultCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "NO_LIVE_NODE".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> "Found a node auto-published, but no corresponding live node exists".equals(l[HEADER_ERROR_MESSAGE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityDefaultCheck", "NO_LIVE_NODE_AUTO_PUBLISH", workspace, targetFolder);
    }

    private void lockSanityCheckDeletionLockOnI18N(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "LockSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "DELETION_LOCK_ON_I18N".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "LockSanityCheck", "DELETION_LOCK_ON_I18N", workspace, targetFolder);
    }

    private void lockSanityCheckInconsistentLock(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(ScriptInputExtractionCommand::unescapeCSV)
                .filter(l -> "LockSanityCheck".equals(l[HEADER_CHECK_ID]))
                .filter(l -> "INCONSISTENT_LOCK".equals(l[HEADER_ERROR_TYPE]))
                .filter(l -> workspace.equals(l[HEADER_WORKSPACE]))
                .map(l -> l[HEADER_NODE_IDENTIFIER])
                .collect(Collectors.toList());
        save(txtLines, "LockSanityCheck", "INCONSISTENT_LOCK", workspace, targetFolder);
    }

    private static void unescapeCSV(String[] line) {
        for (int i = 0; i < line.length; i++) {
            final String s = line[i];
            line[i] = StringUtils.substring(s, 1, s.length() - 1);
        }
    }

    private void save(List<String> lines, String check, String error, String workspace, File targetFolder) {
        final String filename = Stream.of(check, error, workspace).filter(StringUtils::isNotBlank).collect(Collectors.joining("-", "", ".txt"));
        final File file = new File(targetFolder, filename);

        if (CollectionUtils.isEmpty(lines)) {
            if (file.exists()) {
                if (overrideFiles) {
                    FileUtils.deleteQuietly(file);
                    logger.warn("{} not needed, deleted", filename);
                } else {
                    logger.warn("{} not needed, to be deleted manually", filename);
                }
            } else {
                logger.warn("{} not needed", filename);
            }
            return;
        }

        if (file.exists() && !overrideFiles) {
            logger.warn("{} already exists", filename);
            return;
        }

        try {
            FileUtils.writeLines(file, StandardCharsets.UTF_8.name(), lines.stream().distinct().collect(Collectors.toList()));
            logger.info("{} saved", filename);
        } catch (IOException e) {
            logger.error("", e);
        }
    }
}
