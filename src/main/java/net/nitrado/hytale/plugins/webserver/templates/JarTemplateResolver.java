package net.nitrado.hytale.plugins.webserver.templates;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;

import java.nio.file.Path;
import java.util.Map;

/**
 * A Thymeleaf template resolver that loads templates directly from a specific JAR file.
 * <p>
 * Unlike {@link org.thymeleaf.templateresolver.ClassLoaderTemplateResolver}, this resolver
 * reads templates exclusively from the specified JAR file, avoiding unpredictable behavior
 * caused by parent classloader delegation.
 * </p>
 */
public final class JarTemplateResolver extends AbstractConfigurableTemplateResolver {

    private final Path jarPath;

    /**
     * Creates a new JarTemplateResolver for the specified JAR file.
     *
     * @param jarPath the path to the JAR file to load templates from
     */
    public JarTemplateResolver(Path jarPath) {
        this.jarPath = jarPath;
    }

    @Override
    protected ITemplateResource computeTemplateResource(
            IEngineConfiguration configuration,
            String ownerTemplate,
            String template,
            String resourceName,
            String characterEncoding,
            Map<String, Object> templateResolutionAttributes) {

        return new JarTemplateResource(jarPath, resourceName, characterEncoding);
    }
}

