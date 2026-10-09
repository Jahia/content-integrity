package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.services.Utils;

import java.util.List;

@Service
public class CheckIdCompleter extends SimpleCompleter {

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        return Utils.getContentIntegrityService().getContentIntegrityChecksIdentifiers(false);
    }
}
