package com.cni.entity;

import com.cni.audit.AuditService;
import com.cni.casefile.CaseAccessService;
import com.cni.casefile.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController @Transactional @RequestMapping("/api/v1/entities") @RequiredArgsConstructor
public class EntityController {
 private final EntityRepository repo; private final CaseRepository cases; private final CaseAccessService access; private final AuditService audit; private final CaseEntityRepository caseEntities;

 @GetMapping public List<Map<String,Object>> list(@RequestParam(required=false) UUID caseId, Authentication a){
   if(caseId!=null){ access.require(caseId,a); return caseEntities.findByInvestigationCase_IdOrderByEntity_PrimaryNameAsc(caseId).stream().map(ce->view(ce.getEntity(),ce)).toList(); }
   return repo.findAllByOrderByPrimaryNameAsc().stream().flatMap(e->caseEntities.findByEntity_Id(e.getId()).stream().filter(ce->visibleCase(ce.getInvestigationCase().getId(),a)).map(ce->view(e,ce))).toList();
 }
 @GetMapping("/search") public List<Map<String,Object>> search(@RequestParam String query,@RequestParam(required=false) EntityType type,Authentication a){
   if(query==null||query.trim().length()<2)return List.of();
   var matches=type==null
       ? repo.findTop50ByPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(query.trim())
       : repo.findTop50ByEntityTypeAndPrimaryNameContainingIgnoreCaseOrderByPrimaryNameAsc(type,query.trim());
   return matches.stream()
       .map(e -> {
           var visibleAssociation = caseEntities.findByEntity_Id(e.getId()).stream()
               .filter(ce -> visibleCase(ce.getInvestigationCase().getId(), a))
               .findFirst();
           return visibleAssociation.map(ce -> view(e, ce)).orElseGet(() -> masterView(e));
       })
       .limit(50)
       .toList();
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id,@RequestParam(required=false) UUID caseId,Authentication a){
   var e=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Entity not found"));
   CaseEntity ce=null; if(caseId!=null){access.require(caseId,a); ce=caseEntities.findByInvestigationCase_IdAndEntity_Id(caseId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Entity is not registered in this case"));} else { ce=caseEntities.findByEntity_Id(id).stream().filter(x->visibleCase(x.getInvestigationCase().getId(),a)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Entity is not accessible"));}
   audit.log(a,"ENTITY_VIEWED","ENTITY",id,e.getReferenceCode()); return view(e,ce);
 }
 @PostMapping public Map<String,Object> create(@RequestBody CreateEntityRequest r,Authentication a){
   if(r.caseId()==null||r.entityType()==null||blank(r.primaryName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Case, entity type and name are required");
   var c=access.require(r.caseId(),a); Instant now=Instant.now();
   var e=repo.save(IntelligenceEntity.builder().investigationCase(null).referenceCode(uniqueCode()).mapCode(uniqueMapCode()).entityType(r.entityType()).primaryName(r.primaryName().trim()).alias(n(r.alias())).caseRole(n(r.caseRole())).dateOfBirth(r.dateOfBirth()).gender(n(r.gender())).nationality(n(r.nationality())).phone(n(r.phone())).email(n(r.email())).description(n(r.description())).status(d(r.status(),"ACTIVE")).confidence(r.confidence()==null?null:clamp(r.confidence())).locationLabel(n(r.locationLabel())).locationLat(r.locationLat()).locationLng(r.locationLng()).sourceReference(n(r.sourceReference())).createdAt(now).updatedAt(now).build());
   var ce=caseEntities.save(CaseEntity.builder().investigationCase(c).entity(e).caseRole(n(r.caseRole())).registeredAt(now).status(d(r.status(),"ACTIVE")).confidence(r.confidence()==null?null:clamp(r.confidence())).sourceReference(n(r.sourceReference())).build());
   audit.log(a,"ENTITY_CREATED","ENTITY",e.getId(),e.getReferenceCode()); return view(e,ce);
 }
 private Map<String,Object> masterView(IntelligenceEntity e){Map<String,Object> m=new LinkedHashMap<>();m.put("id",e.getId());m.put("referenceCode",e.getReferenceCode());m.put("mapCode",e.getMapCode());m.put("entityType",e.getEntityType().name());m.put("primaryName",e.getPrimaryName());m.put("alias",e.getAlias());m.put("caseRole",null);m.put("dateOfBirth",e.getDateOfBirth());m.put("gender",e.getGender());m.put("nationality",e.getNationality());m.put("phone",e.getPhone());m.put("email",e.getEmail());m.put("description",e.getDescription());m.put("status",e.getStatus());m.put("confidence",e.getConfidence());m.put("caseId",null);m.put("caseNumber",null);m.put("caseTitle",null);m.put("registeredAt",e.getCreatedAt());m.put("updatedAt",e.getUpdatedAt());m.put("locationLabel",e.getLocationLabel());m.put("locationLat",e.getLocationLat());m.put("locationLng",e.getLocationLng());m.put("sourceReference",null);return m;}
 private Map<String,Object> view(IntelligenceEntity e,CaseEntity ce){Map<String,Object> m=new LinkedHashMap<>();m.put("id",e.getId());m.put("referenceCode",e.getReferenceCode());m.put("mapCode",e.getMapCode());m.put("entityType",e.getEntityType().name());m.put("primaryName",e.getPrimaryName());m.put("alias",e.getAlias());m.put("caseRole",ce!=null?ce.getCaseRole():e.getCaseRole());m.put("dateOfBirth",e.getDateOfBirth());m.put("gender",e.getGender());m.put("nationality",e.getNationality());m.put("phone",e.getPhone());m.put("email",e.getEmail());m.put("description",e.getDescription());m.put("status",ce!=null?ce.getStatus():e.getStatus());m.put("confidence",ce!=null?ce.getConfidence():e.getConfidence());m.put("caseId",ce!=null?ce.getInvestigationCase().getId():null);m.put("caseNumber",ce!=null?ce.getInvestigationCase().getCaseNumber():null);m.put("caseTitle",ce!=null?ce.getInvestigationCase().getTitle():null);m.put("registeredAt",ce!=null?ce.getRegisteredAt():e.getCreatedAt());m.put("updatedAt",e.getUpdatedAt());m.put("locationLabel",e.getLocationLabel());m.put("locationLat",e.getLocationLat());m.put("locationLng",e.getLocationLng());m.put("sourceReference",ce!=null?ce.getSourceReference():e.getSourceReference());return m;}
 private String uniqueCode(){String a="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; while(true){StringBuilder b=new StringBuilder(); for(int i=0;i<6;i++)b.append(a.charAt(new Random().nextInt(a.length()))); if(!repo.existsByReferenceCode(b.toString()))return b.toString();}}
 private String uniqueMapCode(){String a="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; Set<String> existing=new HashSet<>(); repo.findAll().forEach(e->{if(e.getMapCode()!=null)existing.add(e.getMapCode());}); while(true){StringBuilder b=new StringBuilder();for(int i=0;i<3;i++)b.append(a.charAt(new Random().nextInt(a.length())));if(!existing.contains(b.toString()))return b.toString();}}
 private static Double clamp(Double v){return Math.max(0,Math.min(1,v));} private static String n(String v){return blank(v)?null:v.trim();} private static String d(String v,String d){return blank(v)?d:v.trim();} private boolean visibleCase(UUID caseId, Authentication a){try{access.require(caseId,a);return true;}catch(Exception ex){return false;}} private static boolean blank(String v){return v==null||v.trim().isEmpty();}
 public record CreateEntityRequest(UUID caseId,EntityType entityType,String primaryName,String alias,String caseRole,java.time.LocalDate dateOfBirth,String gender,String nationality,String phone,String email,String description,String status,Double confidence,String locationLabel,Double locationLat,Double locationLng,String sourceReference){}
}
