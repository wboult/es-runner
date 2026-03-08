package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistroDescriptorTest {

    @Test
    void buildsFileNameFromVersionAndOs() {
        DistroDescriptor descriptor = DistroDescriptor.forVersion("9.2.4");
        String fileName = descriptor.fileName();

        assertTrue(fileName.contains("9.2.4"));
        assertTrue(fileName.contains(Os.classifier()));
        assertTrue(fileName.endsWith("." + Os.archiveExtension()));
    }

    @Test
    void preservesQueryStringWhenBuildingDownloadUri() {
        DistroDescriptor descriptor = DistroDescriptor.forVersion("9.2.4");

        URI uri = descriptor.downloadUri("https://example.blob.core.windows.net/es/?sv=token");

        assertEquals("sv=token", uri.getQuery());
        assertTrue(uri.getPath().endsWith("/" + descriptor.fileName()));
    }

    @Test
    void preservesAuthorityForBucketNamesThatAreNotValidHosts() {
        DistroDescriptor descriptor = DistroDescriptor.forVersion("9.2.4");

        URI uri = descriptor.downloadUri("gs://news-scanner-457301_cloudbuild/elasticsearch/");

        assertEquals("news-scanner-457301_cloudbuild", uri.getRawAuthority());
        assertTrue(uri.toString().startsWith("gs://news-scanner-457301_cloudbuild/"));
    }
}
