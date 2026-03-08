package com.elastic.runner;

import java.net.URI;

final class DistroDescriptor {
    private final String version;
    private final String classifier;
    private final String extension;

    private DistroDescriptor(String version, String classifier, String extension) {
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
    }

    static DistroDescriptor forVersion(String version) {
        return new DistroDescriptor(version, Os.classifier(), Os.archiveExtension());
    }

    String fileName() {
        return "elasticsearch-" + version + "-" + classifier + "." + extension;
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
