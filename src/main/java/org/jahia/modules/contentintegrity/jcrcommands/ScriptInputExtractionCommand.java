package org.jahia.modules.contentintegrity.jcrcommands;

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
import java.util.List;
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

    @Override
    public Object execute() throws Exception {
        final File file = new File(path);
        if (!file.exists()) return null;

        final List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
        //lines.remove(0); // TODO the csv can have no header line

        final File targetFolder = file.getParentFile();
        undeclaredProperties(lines, Constants.EDIT_WORKSPACE, targetFolder);
        undeclaredProperties(lines, Constants.LIVE_WORKSPACE, targetFolder);

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
        return StringUtils.substring(s, 15, s.length() - 1);
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
            FileUtils.writeLines(file, StandardCharsets.UTF_8.name(), lines);
            System.out.println(filename + " saved");
        } catch (IOException e) {
            logger.error("", e);
        }
    }
}
