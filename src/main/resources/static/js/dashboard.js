document.addEventListener("DOMContentLoaded", () => {
    loadPlayerProfile();
});

/**
 * Tải hồ sơ và tài nguyên của người chơi đang đăng nhập.
 * Dữ liệu được lấy từ backend, không sử dụng số dư do frontend tự cung cấp.
 */
async function loadPlayerProfile() {
    const errorElement = document.getElementById("dashboardError");

    try {
        const response = await fetch("/api/player/me", {
            method: "GET",
            headers: {
                "Accept": "application/json"
            },
            credentials: "same-origin"
        });

        if (!response.ok) {
            throw new Error("Không thể tải hồ sơ người chơi");
        }

        const profile = await response.json();

        renderPlayerProfile(profile);

    } catch (error) {
        console.error("Load player profile failed:", error);

        errorElement.hidden = false;
    }
}

/**
 * Hiển thị thông tin hồ sơ người chơi lên Dashboard.
 *
 * @param {Object} profile Dữ liệu hồ sơ nhận từ backend
 */
function renderPlayerProfile(profile) {
    document.getElementById("playerNickname").textContent =
        profile.nickname || "Người tu luyện";

    document.getElementById("playerId").textContent =
        profile.playerId ?? "---";

    document.querySelector(".player-level span").textContent =
        profile.playerLevel ?? 1;

    const resources = profile.resources || [];

    resources.forEach(resource => {
        const element = document.getElementById(
            `resource-${resource.resourceCode}`
        );

        if (element) {
            element.textContent = formatResource(resource.balance);
        }

        if (resource.resourceCode === "PLAYER_EXP") {
            document.getElementById("playerExp").textContent =
                formatResource(resource.balance);
        }
    });
}

/**
 * Định dạng số lượng tài nguyên để hiển thị trên giao diện.
 *
 * @param {number|string} value Số lượng tài nguyên
 * @return {string} Giá trị đã định dạng
 */
function formatResource(value) {
    const number = Number(value || 0);

    return new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 2
    }).format(number);
}