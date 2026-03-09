package io.github.wboult.esrunner;

import java.net.URI;

final class DistroDescriptor {
    private final DistroFamily family;
    private final String version;
    private final String classifier;
    private final String extension;

    private DistroDescriptor(DistroFamily family, String version, String classifier, String extension) {
        this.family = family;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
    }

    static DistroDescriptor forVersion(String version) {
        return forVersion(DistroFamily.ELASTICSEARCH, version);
    }

    static DistroDescriptor forVersion(DistroFamily family, String version) {
        return new DistroDescriptor(family, version, family.classifier(), Os.archiveExtension());
    }

    String fileName() {
        return family.archivePrefix() + "-" + version + "-" + classifier + "." + extension;
    }

    URI downloadUri(String baseUrl) {
        URI base = URI.create(baseUrl);
        String path = base.getPath() == null ? "" : base.getPath();
        String normalizedPath = path.endsWith("/") ? path : path + "/";
        String downloadPath = normalizedPath + fileName();
        try {
            return new URI(
                    base.getScheme(),
                    base.getRawAuthority(),
                    downloadPath,
                    base.getQuery(),
                    base.getFragment()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid downloadBaseUrl: " + baseUrl, e);
        }
    }
}
