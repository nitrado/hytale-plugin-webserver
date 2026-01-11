package net.nitrado.hytale.plugins.webserver.templates;

import org.thymeleaf.templateresource.ITemplateResource;

import java.io.*;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * A Thymeleaf template resource that reads content directly from a JAR file entry.
 */
public final class JarTemplateResource implements ITemplateResource {

    private final Path jarPath;
    private final String resourceName;
    private final String characterEncoding;

    public JarTemplateResource(Path jarPath, String resourceName, String characterEncoding) {
        this.jarPath = jarPath;
        this.resourceName = resourceName;
        this.characterEncoding = characterEncoding;
    }

    @Override
    public String getDescription() {
        return "JAR resource [" + resourceName + "] in [" + jarPath.getFileName() + "]";
    }

    @Override
    public String getBaseName() {
        var lastSlash = resourceName.lastIndexOf('/');
        var name = lastSlash >= 0 ? resourceName.substring(lastSlash + 1) : resourceName;
        var lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(0, lastDot) : name;
    }

    @Override
    public boolean exists() {
        try (var jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry(resourceName);
            return entry != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Reader reader() throws IOException {
        var jar = new JarFile(jarPath.toFile());
        ZipEntry entry = jar.getEntry(resourceName);

        if (entry == null) {
            jar.close();
            throw new IOException("Resource not found: " + resourceName + " in " + jarPath);
        }

        InputStream inputStream = jar.getInputStream(entry);

        // Wrap to ensure JAR is closed when reader is closed
        return new InputStreamReader(inputStream, characterEncoding) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    jar.close();
                }
            }
        };
    }

    @Override
    public ITemplateResource relative(String relativeLocation) {
        // Resolve relative path from current resource
        var lastSlash = resourceName.lastIndexOf('/');
        String basePath = lastSlash >= 0 ? resourceName.substring(0, lastSlash + 1) : "";
        return new JarTemplateResource(jarPath, basePath + relativeLocation, characterEncoding);
    }
}

