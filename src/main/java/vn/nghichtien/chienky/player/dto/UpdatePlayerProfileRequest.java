package vn.nghichtien.chienky.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdatePlayerProfileRequest {

    @NotBlank(message = "Nickname không được để trống")
    @Size(min = 3, max = 30, message = "Nickname phải từ 3 đến 30 ký tự")
    @Pattern(
            regexp = "^[\\p{L}\\p{N}_ ]+$",
            message = "Nickname chỉ được chứa chữ, số, dấu gạch dưới và khoảng trắng"
    )
    private String nickname;

    @NotBlank(message = "Avatar code không được để trống")
    @Size(max = 50, message = "Avatar code không được vượt quá 50 ký tự")
    @Pattern(
            regexp = "^[A-Z0-9_]+$",
            message = "Avatar code không hợp lệ"
    )
    private String avatarCode;

    protected UpdatePlayerProfileRequest() {
    }

    /**
     * Khởi tạo yêu cầu cập nhật nickname và avatar của người chơi.
     *
     * @param nickname Nickname mới
     * @param avatarCode Mã avatar mới
     */
    public UpdatePlayerProfileRequest(String nickname, String avatarCode) {
        this.nickname = nickname;
        this.avatarCode = avatarCode;
    }

    /**
     * Lấy nickname mới.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Lấy mã avatar mới.
     */
    public String getAvatarCode() {
        return avatarCode;
    }
}