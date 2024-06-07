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

    @Argument(required = true)
    private String path;

    private Map<String, String> values = new HashMap<>();

    @Override
    public Object execute() throws Exception {
        final File file = new File(path);
        if (!file.exists()) return null;

        final File targetFolder = file.getParentFile();
        final File valuesFile = new File(targetFolder, "values.txt");
        if (valuesFile.exists()) {
            FileUtils.readLines(valuesFile, StandardCharsets.UTF_8).stream()
                    .filter(StringUtils::isNotBlank)
                    .filter(l -> !l.startsWith("#"))
                    .filter(l -> l.contains(";"))
                    .map(l -> l.split(";"))
                    .forEach(l -> values.put(l[0], l[1]));
        }

        final List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
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

        values.clear();
        return null;
    }

    private void undeclaredProperties(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "PropertyDefinitionsSanityCheck".equals(l[0]))
                .filter(l -> "UNDECLARED_PROPERTY".equals(l[2]))
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4] + ";" + l[9] + ";" + extractUndeclaredPropName(l[11]))
                .collect(Collectors.toList());
        save(txtLines, "PropertyDefinitionsSanityCheck", "UNDECLARED_PROPERTY", workspace, targetFolder);
    }

    private String extractUndeclaredPropName(String s) {
        //  {property-name=j:sceneType}
        return StringUtils.substring(s, "{property-name=".length(), s.length() - 1);
    }

    private void undeployedModules(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "UndeployedModulesReferencesCheck".equals(l[0]))
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4] + ";" + extractUndeployedModule(l[11]))
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
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4])
                .collect(Collectors.toList());
        save(txtLines, "ChildNodeDefinitionsSanityCheck", null, workspace, targetFolder);
    }

    private void undeclaredMixins(List<String> lines, String workspace, File targetFolder) {
        final List<String> txtLines = lines.stream()
                .map(l -> l.split(";"))
                .peek(this::unescapeCSV)
                .filter(l -> "UndeclaredNodeTypesCheck".equals(l[0]))
                .filter(l -> "Undeclared mixin type".equals(l[10]))
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4] + ";" + extractUndeclaredMixin(l[11]))
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
                .filter(l -> "Undeclared primary type".equals(l[10]))
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4])
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
                .filter(l -> workspace.equals(l[3]))
                .map(l -> {
                    final String defaultValue = values.get(l[11]);
                    if (StringUtils.isBlank(defaultValue)) {
                        System.out.println("No default value for " + l[11]);
                        return null;
                    }
                    return l[4] + ";" + l[9] + ";" + extractEmptyMandatoryProperty(l[11]) + ";" + defaultValue;
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
                .filter(l -> workspace.equals(l[3]))
                .map(l -> {
                    final String key = l[11] + l[12];
                    final String defaultValue = values.get(key);
                    if (StringUtils.isBlank(defaultValue)) {
                        System.out.println("No default value for " + key);
                        return null;
                    }
                    return l[4] + ";" + l[9] + ";" + extractInvalidValueConstraintProperty(l[11]) + ";" + defaultValue;
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
                .filter(l -> workspace.equals(l[3]))
                .map(l -> l[4])
                .collect(Collectors.toList());
        save(txtLines, "MarkForDeletionCheck", null, workspace, targetFolder);
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
