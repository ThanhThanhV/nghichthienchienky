document.addEventListener("DOMContentLoaded", () => {
    loadPlayerProfile();
});

/** Tải hồ sơ và tài nguyên của người chơi đang đăng nhập. */
async function loadPlayerProfile() {
    const errorElement = document.getElementById("dashboardError");

    try {
        const response = await fetch("/api/player/me", {
            method: "GET",
            headers: { "Accept": "application/json" },
            credentials: "same-origin"
        });

        if (!response.ok) {
            throw new Error("Không thể tải hồ sơ người chơi (HTTP " + response.status + ").");
        }

        const profile = await response.json();
        renderPlayerProfile(profile);
        loadPlayerPower(profile.playerId);
        if (errorElement) errorElement.hidden = true;
    } catch (error) {
        console.error("Load player profile failed:", error);
        if (errorElement) {
            errorElement.hidden = false;
            errorElement.textContent = error.message || "Không thể tải hồ sơ người chơi.";
        }
    }
}

/** Render cấp độ và số dư tài nguyên được backend trả về. */
function renderPlayerProfile(profile) {
    const playerId = document.getElementById("playerId");
    const playerLevel = document.getElementById("playerLevel");

    if (playerId) playerId.textContent = profile.playerId ?? "---";
    if (playerLevel) playerLevel.textContent = profile.playerLevel ?? 1;

    (profile.resources || []).forEach(resource => {
        const element = document.getElementById("resource-" + resource.resourceCode);
        if (element) element.textContent = formatResource(resource.balance);

        if (resource.resourceCode === "PLAYER_EXP") {
            const expElement = document.getElementById("playerExp");
            if (expElement) expElement.textContent = formatResource(resource.balance);
        }
    });
}

/** Định dạng số lượng tài nguyên theo locale Việt Nam. */
function formatResource(value) {
    const number = Number(value || 0);
    return new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 2
    }).format(number);
}


/** Tải lực chiến hiện tại, được tính từ đội hình chính trên server. */
async function loadPlayerPower(playerId) {
    const element = document.getElementById("playerPower");
    if (!element || playerId == null) return;

    try {
        const response = await fetch("/api/power-ranking", {
            method: "GET",
            headers: { "Accept": "application/json" },
            credentials: "same-origin"
        });
        if (!response.ok) throw new Error("Không thể tải lực chiến.");
        const ranking = await response.json();
        const current = ranking.find(item => Number(item.playerId) === Number(playerId));
        element.textContent = formatResource(current?.totalPower || 0);
    } catch (error) {
        console.error("Load player power failed:", error);
        element.textContent = "—";
    }
}
