package io.github.wboult.esrunner;

import java.net.URI;
import java.nio.file.Path;

record ResolvedDistro(
        DistroFamily family,
        String version,
        Path archive,
        String sourceKind,
        String downloadBaseUrl,
        URI downloadUri
) {
}
