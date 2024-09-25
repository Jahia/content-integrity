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

    @Option(name = "-o")
    private boolean overrideFiles;

    @Argument(required = true, description = "CSV file or directory containing it")
    private String path;

    private Map<String, String> values = new HashMap<>();

    @Override
    public Object execute() throws Exception {
        final File paramFile = new File(path);
        if (!paramFile.exists()) return null;

        final File csvFile;
        final File targetFolder;
        if (paramFile.isDirectory()) {
            targetFolder = paramFile;
            final List<File> fileList = FileUtils.listFiles(targetFolder, Collections.singletonList("csv").toArray(new String[0]), false).stream().collect(Collectors.toList());
            if (fileList.size() != 1) {
                System.out.println("Unable to identify the csv file in the specified folder");
                return null;
            }
            csvFile = fileList.get(0);
        } else {
            csvFile = paramFile;
            targetFolder = paramFile.getParentFile();
        }

        final File valuesFile = new File(targetFolder, "values.txt");
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

        undeployedModules(lines, Constants.EDIT_WORKSPACE, targetFolder);
        undeployedModules(lines, Constants.LIVE_WORKSPACE, targetFolder);
        childNodeDefinitions(lines, Constants.EDIT_WORKSPACE, targetFolder);
        childNodeDefinitions(lines, Constants.LIVE_WORKSPACE, targetFolder);
        // ReferencesSanityCheck not a problem for the import
        undeclaredMixins(lines, Constants.EDIT_WORKSPACE, targetFolder);
        undeclaredMixins(lines, Constants.LIVE_WORKSPACE, targetFolder);
        undeclaredPrimaryType(lines, Constants.EDIT_WORKSPACE, targetFolder);
        undeclaredPrimaryType(lines, Constants.LIVE_WORKSPACE, targetFolder);
        emptyMandatoryProperties(lines, Constants.EDIT_WORKSPACE, targetFolder);
        emptyMandatoryProperties(lines, Constants.LIVE_WORKSPACE, targetFolder);
        invalidValueConstraint(lines, Constants.EDIT_WORKSPACE, targetFolder);
        invalidValueConstraint(lines, Constants.LIVE_WORKSPACE, targetFolder);
        undeclaredProperties(lines, Constants.EDIT_WORKSPACE, targetFolder);
        undeclaredProperties(lines, Constants.LIVE_WORKSPACE, targetFolder);
        markedForDeletion(lines, Constants.EDIT_WORKSPACE, targetFolder);
        markedForDeletion(lines, Constants.LIVE_WORKSPACE, targetFolder);
        missingDefaultNode(lines, Constants.LIVE_WORKSPACE, targetFolder);

        values.clear();
        return null;
    }

    private void undeclaredProperties(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[0]))
                .filter(l -> "UNDECLARED_PROPERTY".equals(l[2]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5] + ";" + l[10] + ";" + extractUndeclaredPropName(l[12]))
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "UNDECLARED_PROPERTY", workspace, targetFolder);
    }

    private String extractUndeclaredPropName(String s) {
        //  {property-name=j:sceneType}
        return StringUtils.substring(s, "{property-name=".length(), s.length() - 1);
    }

    private void undeployedModules(List<String> lines, String workspace, File targetFolder) {
        /*
        Since the errors are on some autopublished nodes (site), they are reported only for the default workspace.
        But they need to be fixed in both workspace. As a consequence, we do not filter on the workspace here,
        so that the txt file is generated for the live workspace based on the errors tracked for the default workspace
         */
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "UndeployedModulesReferencesCheck".equals(l[0]))
                //.filter(l -> workspace.equals(l[4]))
                .map(l -> l[5] + ";" + extractUndeployedModule(l[12]))
                .collect(Collectors.toList());
        save(txtLines, "UndeployedModulesReferencesCheck", null, workspace, targetFolder);
    }

    private String extractUndeployedModule(String s) {
        //  {module=v8-modules-helper}
        return StringUtils.substring(s, "{module=".length(), s.length() - 1);
    }

    private void childNodeDefinitions(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "ChildNodeDefinitionsSanityCheck".equals(l[0]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5])
                .collect(Collectors.toList());
        save(txtLines, "ChildNodeDefinitionsSanityCheck", null, workspace, targetFolder);
    }

    private void undeclaredMixins(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "UndeclaredNodeTypesCheck".equals(l[0]))
                .filter(l -> "Undeclared mixin type".equals(l[11]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5] + ";" + extractUndeclaredMixin(l[12]))
                .collect(Collectors.toList());
        save(txtLines, "UndeclaredMixinsCheck", null, workspace, targetFolder);
    }

    private String extractUndeclaredMixin(String s) {
        //  {mixin type=cpg:header}
        return StringUtils.substring(s, "{mixin type=".length(), s.length() - 1);
    }

    private void undeclaredPrimaryType(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "UndeclaredNodeTypesCheck".equals(l[0]))
                .filter(l -> "Undeclared primary type".equals(l[11]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5])
                .collect(Collectors.toList());
        save(txtLines, "UndeclaredPrimaryTypesCheck", null, workspace, targetFolder);
    }

    private String extractUndeclaredPrimaryType(String s) {
        //  {primary type=fwk:newsListNewsReference}
        return StringUtils.substring(s, "{primary type=".length(), s.length() - 1);
    }

    private void emptyMandatoryProperties(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[0]))
                .filter(l -> "EMPTY_MANDATORY_PROPERTY".equals(l[2]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> {
                    final String defaultValue = values.get(l[12]);
                    if (StringUtils.isBlank(defaultValue)) {
                        System.out.println("No default value for " + l[12]);
                        return null;
                    }
                    return l[5] + ";" + l[10] + ";" + extractEmptyMandatoryProperty(l[12]) + ";" + defaultValue;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "EMPTY_MANDATORY_PROPERTY", workspace, targetFolder);
    }

    private String extractEmptyMandatoryProperty(String s) {
        // {declaring-type=iso:title, property-name=jcr:title}
        return StringUtils.substring(s, s.indexOf("property-name=") + "property-name=".length(), s.length() - 1);
    }

    private void invalidValueConstraint(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[0]))
                .filter(l -> "INVALID_VALUE_CONSTRAINT".equals(l[2]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> {
                    final String key = l[12] + l[13];
                    final String defaultValue = values.get(key);
                    if (StringUtils.isBlank(defaultValue)) {
                        System.out.println("No default value for " + key);
                        return null;
                    }
                    return l[5] + ";" + l[10] + ";" + extractInvalidValueConstraintProperty(l[12]) + ";" + defaultValue;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "INVALID_VALUE_CONSTRAINT", workspace, targetFolder);
    }

    private String extractInvalidValueConstraintProperty(String s) {
        // {constraints=[transparent, light, dark], declaring-type=fwk:allowedInCarousel, property-name=bgcolorText}
        return extractEmptyMandatoryProperty(s);
    }

    private void markedForDeletion(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "MarkForDeletionCheck".equals(l[0]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5])
                .collect(Collectors.toList());
        save(txtLines, "MarkForDeletionCheck", null, workspace, targetFolder);
    }

    private void missingDefaultNode(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "PublicationSanityLiveCheck".equals(l[0]))
                .filter(l -> "NO_DEFAULT_NODE".equals(l[2]))
                .filter(l -> workspace.equals(l[4]))
                .map(l -> l[5])
                .collect(Collectors.toList());
        save(txtLines, "PublicationSanityLiveCheck", "NO_DEFAULT_NODE", workspace, targetFolder);
    }

    private void unescapeCSV(String[] line) {
        for (int i =0; i< line.length; i++) {
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
                    file.delete();
                    System.out.println(filename + " not needed, deleted");
                } else {
                    System.out.println(filename + " not needed, to be deleted manually");
                }
            } else {
                System.out.println(filename + " not needed");
            }
            return;
        }

        if (file.exists() && !overrideFiles) {
            System.out.println(filename + " already exists");
            return;
        }

        try {
            FileUtils.writeLines(file, StandardCharsets.UTF_8.name(), lines.stream().distinct().collect(Collectors.toList()));
            System.out.println(filename + " saved");
        } catch (IOException e) {
            logger.error("", e);
        }
    }
}
