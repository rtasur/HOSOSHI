package com.cni.casefile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {
    private final CaseService s;

    @GetMapping
    public List<CaseService.CaseResponse> list(Authentication a,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String priority) {
        return s.list(a, sort, priority);
    }

    @GetMapping("/bin")
    public List<CaseService.CaseResponse> bin(Authentication a) {
        return s.bin(a);
    }

    @PostMapping
    public CaseService.CaseResponse create(@Valid @RequestBody CaseService.CreateCaseRequest r, Authentication a) {
        return s.create(r, a);
    }

    @GetMapping("/{id}")
    public CaseService.CaseResponse get(@PathVariable UUID id, Authentication a) { return s.get(id, a); }

    @PutMapping("/{id}")
    public CaseService.CaseResponse update(@PathVariable UUID id, @RequestBody CaseService.UpdateCaseRequest r, Authentication a) {
        return s.update(id, r, a);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable UUID id, Authentication a) {
        s.delete(id, a);
        return Map.of("status", "moved-to-bin");
    }

    @PostMapping("/{id}/restore")
    public CaseService.CaseResponse restore(@PathVariable UUID id, Authentication a) { return s.restore(id, a); }

    @PostMapping("/{id}/members")
    public Map<String, String> assign(@PathVariable UUID id, @RequestBody CaseService.AssignRequest r, Authentication a) {
        s.assign(id, r, a);
        return Map.of("status", "assigned");
    }

    @GetMapping("/{id}/members")
    public List<CaseService.MemberResponse> members(@PathVariable UUID id, Authentication a) { return s.members(id, a); }

    @DeleteMapping("/{id}/members/{uid}")
    public Map<String, String> remove(@PathVariable UUID id, @PathVariable UUID uid, Authentication a) {
        s.removeMember(id, uid, a);
        return Map.of("status", "removed");
    }
}
