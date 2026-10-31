package vn.nghichtien.chienky.player.dto;

import java.math.BigDecimal;
import java.util.List;

public class PlayerProfileResponse {

    private Long playerId;
    private String nickname;
    private String avatarCode;
    private Integer playerLevel;
    private List<ResourceBalanceResponse> resources;

    /**
     * Khởi tạo DTO thông tin hồ sơ người chơi và danh sách tài nguyên.
     *
     * @param playerId ID hồ sơ
     * @param nickname Tên nhân vật
     * @param avatarCode Mã ảnh đại diện
     * @param playerLevel Cấp độ người chơi
     * @param resources Danh sách số dư tài nguyên
     */
    public PlayerProfileResponse(
            Long playerId,
            String nickname,
            String avatarCode,
            Integer playerLevel,
            List<ResourceBalanceResponse> resources
    ) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.avatarCode = avatarCode;
        this.playerLevel = playerLevel;
        this.resources = resources;
    }

    /**
     * Lấy ID hồ sơ người chơi.
     */
    public Long getPlayerId() {
        return playerId;
    }

    /**
     * Lấy tên nhân vật.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Lấy mã ảnh đại diện.
     */
    public String getAvatarCode() {
        return avatarCode;
    }

    /**
     * Lấy cấp độ người chơi.
     */
    public Integer getPlayerLevel() {
        return playerLevel;
    }

    /**
     * Lấy danh sách tài nguyên.
     */
    public List<ResourceBalanceResponse> getResources() {
        return resources;
    }

    public static class ResourceBalanceResponse {

        private String resourceCode;
        private String resourceName;
        private String resourceType;
        private BigDecimal balance;
        private BigDecimal maxBalance;

        /**
         * Khởi tạo DTO số dư của một loại tài nguyên.
         *
         * @param resourceCode Mã tài nguyên
         * @param resourceName Tên tài nguyên
         * @param resourceType Loại tài nguyên
         * @param balance Số dư hiện tại
         * @param maxBalance Số dư tối đa, có thể null
         */
        public ResourceBalanceResponse(
                String resourceCode,
                String resourceName,
                String resourceType,
                BigDecimal balance,
                BigDecimal maxBalance
        ) {
            this.resourceCode = resourceCode;
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            this.balance = balance;
            this.maxBalance = maxBalance;
        }

        /**
         * Lấy mã tài nguyên.
         */
        public String getResourceCode() {
            return resourceCode;
        }

        /**
         * Lấy tên tài nguyên.
         */
        public String getResourceName() {
            return resourceName;
        }

        /**
         * Lấy loại tài nguyên.
         */
        public String getResourceType() {
            return resourceType;
        }

        /**
         * Lấy số dư hiện tại.
         */
        public BigDecimal getBalance() {
            return balance;
        }

        /**
         * Lấy số dư tối đa.
         */
        public BigDecimal getMaxBalance() {
            return maxBalance;
        }
    }
}