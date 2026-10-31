package vn.nghichtien.chienky.equipment.controller;

import vn.nghichtien.chienky.equipment.dto.EquipEquipmentRequest;
import vn.nghichtien.chienky.equipment.dto.EquipmentResponse;
import vn.nghichtien.chienky.equipment.dto.HeroEquipmentResponse;
import vn.nghichtien.chienky.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/player")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /**
     * Lấy toàn bộ trang bị trong kho của người chơi đăng nhập.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @return danh sách trang bị
     */
    @GetMapping("/equipment")
    public List<EquipmentResponse> getInventory(
            Authentication authentication
    ) {
        return equipmentService.getInventory(authentication.getName());
    }

    /**
     * Lấy trang bị hiện tại của một tướng thuộc người chơi.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @param instanceId ID tướng
     * @return trang bị theo từng slot
     */
    @GetMapping("/heroes/{instanceId}/equipment")
    public HeroEquipmentResponse getHeroEquipment(
            Authentication authentication,
            @PathVariable Long instanceId
    ) {
        return equipmentService.getHeroEquipment(
                authentication.getName(),
                instanceId
        );
    }

    /**
     * Trang bị một vật phẩm trong kho lên tướng.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @param instanceId ID tướng
     * @param request vật phẩm và slot muốn trang bị
     * @return trạng thái trang bị mới
     */
    @PostMapping("/heroes/{instanceId}/equipment")
    public HeroEquipmentResponse equip(
            Authentication authentication,
            @PathVariable Long instanceId,
            @Valid @RequestBody EquipEquipmentRequest request
    ) {
        return equipmentService.equip(
                authentication.getName(),
                instanceId,
                request
        );
    }

    /**
     * Tháo trang bị khỏi một slot của tướng.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @param instanceId ID tướng
     * @param equipmentType slot cần tháo
     * @return trạng thái trang bị sau khi tháo
     */
    @DeleteMapping("/heroes/{instanceId}/equipment/{equipmentType}")
    public HeroEquipmentResponse unequip(
            Authentication authentication,
            @PathVariable Long instanceId,
            @PathVariable String equipmentType
    ) {
        return equipmentService.unequip(
                authentication.getName(),
                instanceId,
                equipmentType
        );
    }

    /**
     * Cường hóa một trang bị trong kho của người chơi.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @param equipmentId ID trang bị
     * @param transactionKey khóa giao dịch chống lặp
     * @return trang bị sau cường hóa
     */
    @PostMapping("/equipment/{equipmentId}/enhance")
    public EquipmentResponse enhance(
            Authentication authentication,
            @PathVariable Long equipmentId,
            @RequestParam String transactionKey
    ) {
        return equipmentService.enhance(
                authentication.getName(),
                equipmentId,
                transactionKey
        );
    }
}