package com.yash.Notifyr.controller;

import com.yash.Notifyr.dto.CampaignRequest;
import com.yash.Notifyr.dto.CampaignResponse;
import com.yash.Notifyr.dto.CampaignStatsResponse;
import com.yash.Notifyr.dto.PageResponse;
import com.yash.Notifyr.security.CurrentUser;
import com.yash.Notifyr.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CampaignRequest request) {
        CampaignResponse response = campaignService.create(request, CurrentUser.get().username());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<CampaignResponse>> getAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(campaignService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getById(@PathVariable Long id){
        return ResponseEntity.ok(campaignService.getById(id));
    }

    // delivery progress: the campaign's notifications counted by status
    @GetMapping("/{id}/stats")
    public ResponseEntity<CampaignStatsResponse> getStats(@PathVariable Long id){
        return ResponseEntity.ok(campaignService.getStats(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody CampaignRequest request){

        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id){
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<CampaignResponse> sendCampaign(@PathVariable Long id){
        CampaignResponse response = campaignService.send(id);
        return ResponseEntity.ok(response);
    }
}
