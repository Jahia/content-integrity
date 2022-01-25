package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class SimpleCompleter implements Completer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCompleter.class);

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final List<String> allowedValues = getAllowedValues(session, commandLine);
        if (CollectionUtils.isEmpty(allowedValues)) return -1;
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(allowedValues);
        } else {
            for (String value : allowedValues) {
                if (value.startsWith(argument)) delegate.getStrings().add(value);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }

    public abstract List<String> getAllowedValues(Session session, CommandLine commandLine);
}
