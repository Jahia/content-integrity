package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CheckConfigParamCompleter extends SimpleCompleter {

    private static final Logger logger = LoggerFactory.getLogger(CheckConfigParamCompleter.class);

    private static final String OPTION = "-id";

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        final ContentIntegrityCheck check = Utils.getContentIntegrityService().getContentIntegrityCheck(getCheckID(commandLine));
        if (!(check instanceof ContentIntegrityCheck.IsConfigurable)) return null;

        final ContentIntegrityCheck.IsConfigurable configurableCheck = (ContentIntegrityCheck.IsConfigurable) check;
        return new ArrayList<>(configurableCheck.getConfigurations().getConfigurationNames());
    }

    private long getCheckID(CommandLine commandLine) {
        if (commandLine.getArguments().length > 0) {
            final List<String> arguments = Arrays.asList(commandLine.getArguments());
            if (arguments.contains(OPTION)) {
                final int index = arguments.indexOf(OPTION);
                if (arguments.size() > index) {
                    try {
                        return Long.parseLong(arguments.get(index + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return -1L;
    }
}
