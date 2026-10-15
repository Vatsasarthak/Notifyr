package com.yash.Notifyr.controller;

import com.yash.Notifyr.dto.PageResponse;
import com.yash.Notifyr.dto.RecipientRequest;
import com.yash.Notifyr.dto.RecipientResponse;
import com.yash.Notifyr.dto.RecipientStatusUpdateRequest;
import com.yash.Notifyr.service.RecipientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recipients")
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientService recipientService;

    @PostMapping
    public ResponseEntity<RecipientResponse> create(@Valid @RequestBody RecipientRequest request){
        RecipientResponse response = recipientService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<RecipientResponse>> getAll(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable){
        if(search != null && !search.isEmpty()){
            return ResponseEntity.ok(recipientService.search(search, pageable));
        }
        return ResponseEntity.ok(recipientService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipientResponse> getById(@PathVariable Long id){
        return ResponseEntity.ok(recipientService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecipientResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody RecipientRequest request){
        return ResponseEntity.ok(recipientService.update(id, request));
    }

    // update status
    @PutMapping("/{id}/status")
    public ResponseEntity<RecipientResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody RecipientStatusUpdateRequest request
    ){
        return ResponseEntity.ok(recipientService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        recipientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
