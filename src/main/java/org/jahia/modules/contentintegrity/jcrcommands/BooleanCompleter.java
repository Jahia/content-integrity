package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

@Service
public class BooleanCompleter implements Completer {

    private static final Logger logger = LoggerFactory.getLogger(BooleanCompleter.class);

    static final List<String> VALUES = Arrays.asList("true", "false");

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(VALUES);
        } else {
            for (String v : VALUES) {
                if (v.startsWith(argument)) delegate.getStrings().add(v);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }

}
