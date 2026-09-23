package com.cni.common;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared validation for user-uploaded source/evidence files.
 *
 * The prototype deliberately accepts a small allowlist of formats so that the
 * ingest and evidence paths have the same security rules.
 */
public final class FileValidation {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "txt", "csv", "json", "png", "jpg", "jpeg");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "text/plain",
        "text/csv",
        "application/json",
        "image/png",
        "image/jpeg");

    private FileValidation() {
    }

    public static String validateAndSanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "File exceeds the maximum allowed size of 20 MB.");
        }

        String original = file.getOriginalFilename() == null
            ? "upload.bin"
            : file.getOriginalFilename();

        String safeName = Path.of(original).getFileName().toString();
        safeName = safeName.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", "_");

        if (safeName.isBlank()) {
            safeName = "upload.bin";
        }

        int dot = safeName.lastIndexOf('.');
        if (dot <= 0 || dot == safeName.length() - 1) {
            throw new IllegalArgumentException(
                "Uploaded file must have a supported extension: PDF, TXT, CSV, JSON, PNG or JPEG.");
        }

        String extension = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                "Unsupported file type. Allowed: PDF, TXT, CSV, JSON, PNG and JPEG.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
            && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))
            && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Uploaded file content type is not allowed.");
        }

        return safeName;
    }

    public static Path safeChild(Path root, String storedName) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path child = normalizedRoot.resolve(storedName).normalize();
        if (!child.startsWith(normalizedRoot)) {
            throw new AccessDeniedException("Invalid file path");
        }
        return child;
    }
}
