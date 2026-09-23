package com.cni.ingestion;

import com.cni.audit.AuditService;
import com.cni.casefile.CaseAccessService;
import com.cni.common.FileValidation;
import com.cni.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
@Transactional
public class IngestionController {
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "FIR", "CDR", "FINANCIAL_TRANSACTION", "SURVEILLANCE",
            "SOCIAL_INTELLIGENCE", "POLICE_REPORT", "SYNTHETIC_DOCUMENT", "OTHER");

    private final IngestionRepository repo;
    private final CaseAccessService access;
    private final UserRepository users;
    private final AuditService audit;

    @Value("${app.storage.path:./data/uploads}")
    private String storage;

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam UUID caseId, Authentication authentication) {
        access.require(caseId, authentication);
        return repo.findByInvestigationCase_IdOrderByCreatedAtDesc(caseId).stream().map(this::view).toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','DATA_OPERATOR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam UUID caseId,
            @RequestParam String sourceType,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String notes,
            Authentication authentication) throws Exception {

        var investigationCase = access.require(caseId, authentication);
        var user = users.findByUsername(authentication.getName()).orElseThrow();
        String normalizedSourceType = validateSourceType(sourceType);

        String fileName = "Manual entry";
        String storedName = null;
        String sha256 = null;
        String contentType = null;
        long sizeBytes = 0;
        long records = 0;

        if (file != null && !file.isEmpty()) {
            fileName = FileValidation.validateAndSanitize(file);
            Path directory = Paths.get(storage).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            storedName = UUID.randomUUID() + "-" + fileName;
            Path target = FileValidation.safeChild(directory, storedName);
            file.transferTo(target);
            sha256 = sha256(target);
            contentType = Objects.toString(file.getContentType(), "application/octet-stream").toLowerCase(Locale.ROOT);
            sizeBytes = file.getSize();
            records = Math.max(1, sizeBytes / 80);
        }

        var saved = repo.save(DataIngestion.builder()
                .investigationCase(investigationCase)
                .createdBy(user)
                .sourceType(normalizedSourceType)
                .fileName(fileName)
                .recordCount(records)
                .status("REVIEW_REQUIRED")
                .notes(notes)
                .storedName(storedName)
                .sha256(sha256)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .createdAt(Instant.now())
                .build());

        String auditAction = storedName == null
                ? "MANUAL_SOURCE_CREATED"
                : "SOURCE_DOCUMENT_UPLOADED";
        audit.log(authentication, auditAction, "INGESTION", saved.getId(), saved.getFileName());
        return view(saved);
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','DATA_OPERATOR')")
    @PostMapping("/manual")
    public Map<String, Object> manual(@RequestBody Req request, Authentication authentication) {
        var investigationCase = access.require(request.caseId(), authentication);
        var user = users.findByUsername(authentication.getName()).orElseThrow();
        String normalizedSourceType = validateSourceType(request.sourceType());

        var saved = repo.save(DataIngestion.builder()
                .investigationCase(investigationCase)
                .createdBy(user)
                .sourceType(normalizedSourceType)
                .fileName("Manual entry")
                .recordCount(Math.max(0, request.recordCount()))
                .status("REVIEW_REQUIRED")
                .notes(request.notes())
                .createdAt(Instant.now())
                .build());

        audit.log(authentication, "MANUAL_SOURCE_CREATED", "INGESTION", saved.getId(), saved.getSourceType());
        return view(saved);
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','DATA_OPERATOR','INTELLIGENCE_ANALYST')")
    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable UUID id, Authentication authentication) {
        var ingestion = repo.findById(id).orElseThrow();
        access.require(ingestion.getInvestigationCase().getId(), authentication);
        ingestion.setStatus("IMPORTED");
        audit.log(authentication, "INGESTION_APPROVED", "INGESTION", id, ingestion.getFileName());
        return view(repo.save(ingestion));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> viewFile(@PathVariable UUID id, Authentication authentication) {
        var ingestion = repo.findById(id).orElseThrow(() ->
                new java.util.NoSuchElementException("Source document not found"));
        access.require(ingestion.getInvestigationCase().getId(), authentication);
        if (ingestion.getStoredName() == null || ingestion.getStoredName().isBlank()) {
            throw new java.util.NoSuchElementException("This ingestion record does not contain an uploaded file.");
        }

        Path root = Paths.get(storage).toAbsolutePath().normalize();
        Path filePath = FileValidation.safeChild(root, ingestion.getStoredName());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new java.util.NoSuchElementException("Source document was not found on disk.");
        }

        audit.log(authentication, "SOURCE_DOCUMENT_VIEWED", "INGESTION", id, ingestion.getFileName());
        return ResponseEntity.ok()
                .contentType(resolveMediaType(ingestion.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(ingestion.getFileName()).build().toString())
                .body(resource);
    }

    private Map<String, Object> view(DataIngestion ingestion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ingestion.getId());
        result.put("caseId", ingestion.getInvestigationCase().getId());
        result.put("sourceType", ingestion.getSourceType());
        result.put("fileName", ingestion.getFileName());
        result.put("recordCount", ingestion.getRecordCount());
        result.put("status", ingestion.getStatus());
        result.put("notes", Objects.toString(ingestion.getNotes(), ""));
        result.put("sha256", ingestion.getSha256());
        result.put("contentType", ingestion.getContentType());
        result.put("sizeBytes", ingestion.getSizeBytes());
        result.put("hasFile", ingestion.getStoredName() != null && !ingestion.getStoredName().isBlank());
        result.put("createdAt", ingestion.getCreatedAt());
        return result;
    }

    private String validateSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) throw new IllegalArgumentException("Source type is required");
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_TYPES.contains(normalized)) throw new IllegalArgumentException("Unsupported source type");
        return normalized;
    }

    private String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private MediaType resolveMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(Objects.toString(contentType, MediaType.APPLICATION_OCTET_STREAM_VALUE));
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record Req(UUID caseId, String sourceType, long recordCount, String notes) {}
}
