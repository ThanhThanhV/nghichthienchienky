package vn.nghichtien.chienky.pve.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nghichtien.chienky.pve.dto.PveCampaignOverviewDTO;
import vn.nghichtien.chienky.pve.service.PveCampaignService;

@RestController
@RequestMapping("/api/pve/campaign")
public class PveCampaignController {

    private final PveCampaignService campaignService;

    public PveCampaignController(PveCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public PveCampaignOverviewDTO getOverview(Authentication authentication) {
        return campaignService.getOverview(authentication.getName());
    }
}
