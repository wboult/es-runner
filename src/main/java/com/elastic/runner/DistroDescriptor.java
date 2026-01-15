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
        String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalized + fileName());
    }
}
