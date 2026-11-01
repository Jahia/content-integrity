package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;

import java.util.ArrayList;
import java.util.List;

@Service
public class CheckConfigParamCompleter extends SimpleCompleter {

    @Reference
    private ContentIntegrityService integrityService;

    private static final String OPTION = "-id";

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        final ContentIntegrityCheck check = integrityService.getContentIntegrityCheck(getOptionValue(commandLine, OPTION));
        if (!(check instanceof ContentIntegrityCheck.IsConfigurable)) return null;

        final ContentIntegrityCheck.IsConfigurable configurableCheck = (ContentIntegrityCheck.IsConfigurable) check;
        return new ArrayList<>(configurableCheck.getConfigurations().getConfigurationNames());
    }
}
