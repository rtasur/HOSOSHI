package com.cni.graph;

import com.cni.entity.CaseEntityRepository;
import com.cni.relationship.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GraphSyncService {
    private final Driver driver;
    private final CaseEntityRepository caseEntities;
    private final RelationshipRepository relationships;

    /**
     * Rebuild the Neo4j projection for one authorized case and return it.
     * PostgreSQL remains the source of truth for case/entity metadata; Neo4j
     * is the graph projection used by the Network Explorer.
     */
    @Transactional
    public Map<String, Object> rebuildAndRead(UUID caseId) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                        "MATCH (n:HEntity {caseId:$caseId}) DETACH DELETE n",
                        Map.of("caseId", caseId.toString())
                ).consume();

                var rows = caseEntities.findByInvestigationCase_IdOrderByEntity_PrimaryNameAsc(caseId);
                for (var ce : rows) {
                    var entity = ce.getEntity();
                    Map<String, Object> props = new LinkedHashMap<>();

                    props.put("id", entity.getId().toString());
                    props.put("caseId", caseId.toString());
                    props.put("referenceCode", entity.getReferenceCode());
                    // Keep legacy key for compatibility with older clients.
                    props.put("code", entity.getReferenceCode());
                    props.put("mapCode", entity.getMapCode());
                    props.put("type", entity.getEntityType() != null ? entity.getEntityType().name() : null);
                    props.put("label", entity.getPrimaryName());
                    props.put("role", firstNonBlank(ce.getCaseRole(), entity.getCaseRole()));
                    props.put("status", firstNonBlank(ce.getStatus(), entity.getStatus()));
                    props.put("confidence", ce.getConfidence() != null ? ce.getConfidence() : entity.getConfidence());

                    Instant registeredAt = ce.getRegisteredAt() != null
                            ? ce.getRegisteredAt()
                            : entity.getCreatedAt();
                    if (registeredAt != null) {
                        props.put("registeredAt", registeredAt.toString());
                    }

                    props.put("locationLabel", entity.getLocationLabel());
                    props.put("locationLat", entity.getLocationLat());
                    props.put("locationLng", entity.getLocationLng());
                    props.put("alias", entity.getAlias());
                    props.put("dateOfBirth", entity.getDateOfBirth() != null
                            ? entity.getDateOfBirth().toString()
                            : null);
                    props.put("gender", entity.getGender());
                    props.put("nationality", entity.getNationality());
                    props.put("phone", entity.getPhone());
                    props.put("email", entity.getEmail());
                    props.put("description", entity.getDescription());
                    props.put("sourceReference", firstNonBlank(ce.getSourceReference(), entity.getSourceReference()));

                    props.values().removeIf(Objects::isNull);

                    tx.run(
                            "CREATE (n:HEntity) SET n=$props",
                            Map.of("props", props)
                    ).consume();
                }

                for (var relationship : relationships.findByInvestigationCase_Id(caseId)) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("s", relationship.getSourceEntity().getId().toString());
                    params.put("t", relationship.getTargetEntity().getId().toString());
                    params.put("c", caseId.toString());
                    params.put("id", relationship.getId().toString());
                    params.put("type", relationship.getRelationshipType());
                    params.put("confidence", relationship.getConfidence());
                    params.put("status", relationship.getStatus());
                    params.put("sourceReference", Objects.toString(relationship.getSourceReference(), ""));
                    params.put("observedAt", Objects.toString(relationship.getObservedAt(), ""));
                    params.put("verifiedAt", Objects.toString(relationship.getVerifiedAt(), ""));

                    tx.run(
                            "MATCH (a:HEntity {id:$s,caseId:$c}), "
                                    + "(b:HEntity {id:$t,caseId:$c}) "
                                    + "CREATE (a)-[:CASE_REL "
                                    + "{id:$id,type:$type,confidence:$confidence,status:$status,"
                                    + "sourceReference:$sourceReference,observedAt:$observedAt,verifiedAt:$verifiedAt}]->(b)",
                            params
                    ).consume();
                }
                return null;
            });

            List<Map<String, Object>> nodes = session.executeRead(tx ->
                    tx.run(
                            "MATCH (n:HEntity {caseId:$caseId}) "
                                    + "RETURN n ORDER BY n.label",
                            Map.of("caseId", caseId.toString())
                    ).list(record -> new LinkedHashMap<>(record.get("n").asMap()))
            );

            List<Map<String, Object>> edges = session.executeRead(tx ->
                    tx.run(
                            "MATCH (a:HEntity {caseId:$caseId})-[r:CASE_REL]->"
                                    + "(b:HEntity {caseId:$caseId}) "
                                    + "RETURN r,a.id AS source,b.id AS target",
                            Map.of("caseId", caseId.toString())
                    ).list(record -> {
                        Map<String, Object> edge = new LinkedHashMap<>(record.get("r").asMap());
                        edge.put("source", record.get("source").asString());
                        edge.put("target", record.get("target").asString());
                        return edge;
                    })
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("caseId", caseId);
            response.put("nodes", nodes);
            response.put("edges", edges);
            response.put("generatedAt", Instant.now());
            return response;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
