package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class OutputLevelCompleter extends SimpleCompleter {

    private static final List<String> LEVELS = Arrays.asList("simple", "full");

    @Override
    public List<String> getAllowedValues() {
        return LEVELS;
    }
}
