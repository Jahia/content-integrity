package org.jahia.modules.contentintegrity.config;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@Component(service = ContentIntegrityConfig.class, immediate = true, configurationPid = "org.jahia.modules.contentintegrity")
//@Designate(ocd = ContentIntegrityConfig.Config.class)
public class ContentIntegrityConfig {

    /*
    @ObjectClassDefinition(name = "%configuration.name", description = "%configuration.description", localization = "OSGI-INF/l10n/contentIntegrityConfig")
    public @interface Config {
        @AttributeDefinition(
                name = "%ui.name",
                description = "%ui.description",
                options = {
                        @Option(label = "%ui.value.default", value = "default"),
                        @Option(label = "%ui.value.js", value = "js"),
                        @Option(label = "%ui.value.react", value = "react"),
                }
        )
        String ui() default "default";
    }
    */

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityConfig.class);

    private static final String UI_JS = "js";
    private static final String UI_REACT = "react";
    private static final String DEFAULT_UI = UI_JS;
    private static final Collection<String> validUis = Arrays.asList(UI_JS, UI_REACT);

    private String ui;

    /*
    @Activate
    public void activate(Config config) {
        ui = config.ui();
    }
     */

    @Activate
    public void activate(Map<String, ?> properties) {
        ui = (String) properties.getOrDefault("contentIntegrity.ui", null);
    }

    public String getUi() {
        return validUis.contains(ui) ? ui : DEFAULT_UI;
    }
}
