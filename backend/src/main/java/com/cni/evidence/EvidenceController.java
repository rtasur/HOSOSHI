package com.cni.evidence;

import com.cni.audit.AuditService;
import com.cni.casefile.CaseAccessService;
import com.cni.common.FileValidation;
import com.cni.ingestion.IngestionRepository;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@RestController
@Transactional(rollbackFor = Exception.class)
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
public class EvidenceController {
    private final EvidenceRepository repo;
    private final CaseAccessService access;
    private final UserRepository users;
    private final IngestionRepository ingestionRepository;
    private final AuditService audit;

    @Value("${app.storage.path:./data/uploads}")
    private String storage;

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam UUID caseId, Authentication authentication) {
        access.require(caseId, authentication);
        return repo.findByInvestigationCase_IdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(this::view)
                .toList();
    }

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','DATA_OPERATOR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestParam UUID caseId,
            @RequestParam MultipartFile file,
            Authentication authentication) throws Exception {

        var investigationCase = access.require(caseId, authentication);

        boolean sourceDocumentExists = ingestionRepository
                .existsByInvestigationCase_IdAndStoredNameIsNotNull(caseId);
        if (!sourceDocumentExists) {
            throw new IllegalStateException(
                    "Upload a source document to this case before adding evidence.");
        }

        String safeName = FileValidation.validateAndSanitize(file);
        var user = users.findByUsername(authentication.getName()).orElseThrow();

        Path directory = Paths.get(storage).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String storedName = UUID.randomUUID() + "-" + safeName;
        Path target = FileValidation.safeChild(directory, storedName);
        file.transferTo(target);

        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)));

        EvidenceFile evidence = EvidenceFile.builder()
                .investigationCase(investigationCase)
                .uploadedBy(user)
                .originalName(safeName)
                .storedName(storedName)
                .contentType(Objects.toString(file.getContentType(), "application/octet-stream"))
                .sizeBytes(file.getSize())
                .sha256(sha256)
                .createdAt(Instant.now())
                .build();

        var saved = repo.save(evidence);
        audit.log(authentication, "EVIDENCE_UPLOADED", "EVIDENCE", saved.getId(), safeName);
        return view(saved);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> viewFile(
            @PathVariable UUID id, Authentication authentication) {

        var evidence = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Evidence not found"));

        access.require(evidence.getInvestigationCase().getId(), authentication);

        Path root = Paths.get(storage).toAbsolutePath().normalize();
        Path filePath = FileValidation.safeChild(root, evidence.getStoredName());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists() || !resource.isReadable()) {
            throw new NoSuchElementException("Evidence file not found on disk");
        }

        audit.log(authentication, "EVIDENCE_VIEWED", "EVIDENCE", id, evidence.getOriginalName());

        return ResponseEntity.ok()
                .contentType(resolveMediaType(evidence.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(evidence.getOriginalName())
                                .build()
                                .toString())
                .body(resource);
    }

    private MediaType resolveMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(
                    Objects.toString(contentType, MediaType.APPLICATION_OCTET_STREAM_VALUE));
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private Map<String, Object> view(EvidenceFile evidence) {
        return Map.of(
                "id", evidence.getId(),
                "caseId", evidence.getInvestigationCase().getId(),
                "originalName", evidence.getOriginalName(),
                "contentType", Objects.toString(evidence.getContentType(), "application/octet-stream"),
                "sizeBytes", evidence.getSizeBytes(),
                "sha256", evidence.getSha256(),
                "uploadedBy", evidence.getUploadedBy().getFullName(),
                "createdAt", evidence.getCreatedAt());
    }
}
