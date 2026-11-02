package vn.nghichtien.chienky.hero.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.nghichtien.chienky.hero.dto.CreateHeroRequest;
import vn.nghichtien.chienky.hero.dto.HeroManagementCreatedResponse;
import vn.nghichtien.chienky.hero.service.HeroManagementService;

import java.util.List;

/**
 * API phục vụ giao diện tạo mới tướng.
 * Phân quyền quản trị được bổ sung ở giai đoạn sau theo yêu cầu hiện tại.
 */
@RestController
@RequestMapping("/api/hero-management")
public class HeroManagementController {

    private final HeroManagementService heroManagementService;

    public HeroManagementController(HeroManagementService heroManagementService) {
        this.heroManagementService = heroManagementService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HeroManagementCreatedResponse> create(
            @Valid @RequestPart("request") CreateHeroRequest request,
            @RequestPart("skeleton") MultipartFile skeleton,
            @RequestPart("atlas") MultipartFile atlas,
            @RequestPart("textures") List<MultipartFile> textures
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                heroManagementService.create(request, skeleton, atlas, textures)
        );
    }
}
