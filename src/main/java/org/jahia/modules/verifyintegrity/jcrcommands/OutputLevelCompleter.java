package org.jahia.modules.verifyintegrity.jcrcommands;

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
public class OutputLevelCompleter implements Completer {

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final List<String> levels = Arrays.asList("simple", "full");
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(levels);
        } else {
            for (String date : levels) {
                if (date.startsWith(argument)) delegate.getStrings().add(date);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }
}
