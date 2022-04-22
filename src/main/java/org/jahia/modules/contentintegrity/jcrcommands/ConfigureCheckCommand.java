package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.jcrcommands.completers.BooleanCompleter;
import org.jahia.modules.contentintegrity.jcrcommands.completers.CheckConfigParamCompleter;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "jcr", name = "integrity-configureCheck", description = "Configures a registered check")
@Service
public class ConfigureCheckCommand extends JCRCommandSupport implements Action {

    private static final Logger logger = LoggerFactory.getLogger(ConfigureCheckCommand.class);

    @Reference
    Session session;

    @Option(name = "-id", description = "ID of the check to configure")
    private String checkID;

    @Option(name = "-e", aliases = "--enabled", description = "Specifies if the check has to be enabled/disabled")
    @Completion(BooleanCompleter.class)
    private String enabled;

    @Option(name = "-p", aliases = "--param", description = "Parameter to configure. If no value is specified, the current value is printed out")
    @Completion(CheckConfigParamCompleter.class)
    private String paramName;

    @Option(name = "-v", aliases = "--value", description = "Parameter value to set")
    private String paramValue;

    @Option(name = "-rp", aliases = "--resetParam", description = "Parameter to reset to its default value")
    private String resetParam;

    @Option(name = "-pc", aliases = "--printConfigs", description = "Print all the configurations of the specified check")
    private boolean printAllConfs;

    @Override
    public Object execute() throws Exception {
        final ContentIntegrityCheck integrityCheck = Utils.getContentIntegrityService().getContentIntegrityCheck(checkID);
        if (integrityCheck == null) {
            System.out.println(String.format("No check found for ID %s", checkID));
            return null;
        }

        if (enabled != null) {
            integrityCheck.setEnabled(Boolean.parseBoolean(enabled));
            System.out.println(String.format("%s: %s", integrityCheck.getName(),
                    integrityCheck.isEnabled() ? "enabled" : "disabled"));
        }

        if (printAllConfs) printAllConfs(integrityCheck);

        if (StringUtils.isNotBlank(paramName) || StringUtils.isNotBlank(resetParam)) {
            setParam(integrityCheck);
        }

        return null;
    }

    private void printAllConfs(ContentIntegrityCheck integrityCheck) {
        if (!(integrityCheck instanceof ContentIntegrityCheck.IsConfigurable)) {
            System.out.println(String.format("%s is not configurable", integrityCheck.getName()));
            return;
        }

        final ContentIntegrityCheck.IsConfigurable check = (ContentIntegrityCheck.IsConfigurable) integrityCheck;
        System.out.println(String.format("%s:", integrityCheck.getName()));
        final ContentIntegrityCheckConfiguration configurations = check.getConfigurations();
        for (String name : configurations.getConfigurationNames()) {
            System.out.println(String.format("    %s = %s (%s)",
                    name, configurations.getParameter(name), configurations.getDescription(name)));
        }

    }

    private void setParam(ContentIntegrityCheck integrityCheck) {
        if (StringUtils.isNotBlank(paramName) && StringUtils.isNotBlank(resetParam)) {
            System.out.println("-p and -rp can't be used together");
            return;
        }

        if (!(integrityCheck instanceof ContentIntegrityCheck.IsConfigurable)) {
            System.out.println(String.format("%s is not configurable", integrityCheck.getName()));
            return;
        }
        final ContentIntegrityCheck.IsConfigurable check = (ContentIntegrityCheck.IsConfigurable) integrityCheck;

        if (StringUtils.isNotBlank(paramName)) {
            if (StringUtils.isNotBlank(paramValue))
                check.getConfigurations().setParameter(paramName, paramValue);
            System.out.println(String.format("%s: %s = %s", integrityCheck.getName(), paramName, check.getConfigurations().getParameter(paramName)));
        } else if (StringUtils.isNotBlank(paramValue)) {
            System.out.println("The parameter name is not declared");
        }

        if (StringUtils.isNotBlank(resetParam)) {
            check.getConfigurations().setParameter(resetParam, null);
            System.out.println(String.format("%s: %s = %s", integrityCheck.getName(), resetParam, check.getConfigurations().getParameter(resetParam)));
        }
    }
}
