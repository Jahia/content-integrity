package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class CheckIdCompleter extends SimpleCompleter {

    private static final Logger logger = LoggerFactory.getLogger(CheckIdCompleter.class);

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        return Utils.getContentIntegrityService().getContentIntegrityChecksIdentifiers(false);
    }
}
