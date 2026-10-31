package vn.nghichtien.chienky.player.service;

import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.dto.PlayerProfileResponse;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.resource.domain.PlayerResource;
import vn.nghichtien.chienky.resource.repository.PlayerResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.player.dto.UpdatePlayerProfileRequest;

import java.util.List;

@Service
public class PlayerProfileService {

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerResourceRepository playerResourceRepository;

    public PlayerProfileService(
            PlayerProfileRepository playerProfileRepository,
            PlayerResourceRepository playerResourceRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.playerResourceRepository = playerResourceRepository;
    }

    /**
     * Lấy hồ sơ và toàn bộ số dư tài nguyên theo username đang đăng nhập.
     *
     * @param username Username của tài khoản hiện tại
     * @return Thông tin hồ sơ và tài nguyên người chơi
     */
    @Transactional(readOnly = true)
    public PlayerProfileResponse getMyProfile(String username) {
        PlayerProfile profile = playerProfileRepository
                .findByAccountUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        List<PlayerResource> playerResources =
                playerResourceRepository.findAllByPlayerIdWithDefinition(
                        profile.getId()
                );

        List<PlayerProfileResponse.ResourceBalanceResponse> resources =
                playerResources.stream()
                        .map(resource -> new PlayerProfileResponse.ResourceBalanceResponse(
                                resource.getResourceCode(),
                                resource.getResourceDefinition().getResourceName(),
                                resource.getResourceDefinition().getResourceType(),
                                resource.getBalance(),
                                resource.getResourceDefinition().getMaxBalance()
                        ))
                        .toList();

        return new PlayerProfileResponse(
                profile.getId(),
                profile.getNickname(),
                profile.getAvatarCode(),
                profile.getPlayerLevel(),
                resources
        );
    }

    /**
     * Cập nhật nickname và avatar của người chơi đang đăng nhập.
     * Chỉ thay đổi thông tin hiển thị, không cho phép thay đổi cấp độ hoặc tài nguyên.
     *
     * @param username Username tài khoản đang đăng nhập
     * @param request Thông tin nickname và avatar mới
     * @return Hồ sơ người chơi sau khi cập nhật
     */
    @Transactional
    public PlayerProfileResponse updateMyProfile(
            String username,
            UpdatePlayerProfileRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin cập nhật không hợp lệ");
        }

        PlayerProfile profile = playerProfileRepository
                .findByAccountUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        String nickname = request.getNickname().trim();
        String avatarCode = request.getAvatarCode().trim();

        profile.changeNickname(nickname);
        profile.changeAvatar(avatarCode);

        return getMyProfile(username);
    }
}