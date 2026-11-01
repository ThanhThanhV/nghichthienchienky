# Cấu hình kỹ năng chiến đấu

## Kiến trúc

- `hero_skill_definitions`: định nghĩa kỹ năng theo nhân vật.
- `hero_skill_effects`: các hiệu ứng theo thứ tự thực hiện.
- `DatabaseSkillExecutor`: tải kỹ năng, kiểm tra xác suất, chuyển dữ liệu DB thành cấu hình runtime.
- `BattleEffectProcessor`: thực thi hiệu ứng bằng luật Java hiện có.
- `BattleTurnProcessor`: tự động gọi nội tại theo vòng đời và sự kiện chiến đấu.

Mỗi nhân vật cần có ít nhất một kỹ năng `BASIC`. Chu kỳ lượt hiện tại là `ACTIVE -> ULTIMATE -> BASIC`. Nếu thiếu kỹ năng ACTIVE hoặc ULTIMATE, lượt đó tự động dùng BASIC. SILENCE cũng chuyển hành động sang BASIC.

## Loại kỹ năng

| skill_type | Ý nghĩa |
|---|---|
| BASIC | Đánh thường |
| ACTIVE | Kỹ năng chủ động |
| ULTIMATE | Tuyệt kỹ |
| PASSIVE | Nội tại do engine tự động kích hoạt, người chơi không thao tác |

## Trigger của PASSIVE

Trigger được khai báo trong `config_json` của từng effect. Nếu không khai báo, mặc định là `BATTLE_START`.

| trigger | Thời điểm tự động kích hoạt |
|---|---|
| `BATTLE_START` | Một lần khi trận đấu bắt đầu |
| `ROUND_START` | Đầu mỗi round |
| `BEFORE_ACTION` | Trước khi nhân vật sở hữu nội tại thực hiện hành động |
| `AFTER_ACTION` | Sau khi nhân vật sở hữu nội tại thực hiện hành động |
| `ON_ATTACK` | Khi nhân vật sở hữu nội tại bắt đầu một đòn đánh/kỹ năng |
| `ON_DAMAGED` | Khi nhân vật sở hữu nội tại nhận sát thương HP thực tế |
| `ON_KILL` | Khi nhân vật sở hữu nội tại hạ gục mục tiêu |

Các trigger đều do Java tự kiểm tra; client không gửi yêu cầu kích hoạt. Event phát sinh từ một passive hiện không tự kích hoạt dây chuyền passive khác trong cùng lần dispatch, nhằm tránh vòng lặp phản ứng vô hạn. Nếu cần chuỗi phản kích/counter, nên triển khai bằng cơ chế reaction có giới hạn riêng.

### Ví dụ: nội tại tăng 20% ATK vĩnh viễn khi vào trận

- `skill_type = PASSIVE`
- `effect_type = BUFF`
- `target_type = RANDOM` (với BUFF, target selector chọn đồng minh; RANDOM là chính nhân vật nếu chỉ có một người)
- `multiplier = 0.20`
- `chance = 1`
- `duration = 0`
- `config_json = {"trigger":"BATTLE_START","statType":"ATK","permanent":true}`

### Ví dụ: nội tại có 30% cơ hội tăng 15% ATK trong 2 round sau mỗi lần đánh

- `skill_type = PASSIVE`
- `effect_type = BUFF`
- `target_type = RANDOM`
- `multiplier = 0.15`
- `chance = 0.30`
- `duration = 2`
- `config_json = {"trigger":"ON_ATTACK","statType":"ATK"}`

## Loại hiệu ứng đã có handler

| effect_type | Ý nghĩa | Các trường configJson |
|---|---|---|
| DAMAGE | Gây sát thương | `flatValue` tùy chọn |
| HEAL | Hồi HP | `flatValue` tùy chọn |
| BUFF | Tăng chỉ số | `statType` bắt buộc |
| DEBUFF | Giảm chỉ số | `statType` bắt buộc |
| CC | Khống chế | `statusType` bắt buộc |
| SHIELD | Tạo khiên | `flatValue` tùy chọn |
| EVASION | Tăng né tránh | Không bắt buộc |
| BLOCK | Chặn sát thương | Không bắt buộc |
| REFLECT | Phản sát thương | Cần kiểm thử riêng với DefenseEffectManager |
| IMMUNITY | Miễn nhiễm | `immunityType` bắt buộc |
| DISPEL | Giải hiệu ứng | `dispelType` bắt buộc |

Các enum hợp lệ được định nghĩa trong `BattleEffectType`, `BattleTargetMode`, `BattleStatType`, `BattleStatusType`, `BattleImmunityType` và `BattleDispelType`.

## Target type

Các giá trị hiện có:

- `RANDOM`
- `FRONT`
- `LOWEST_HP`
- `HIGHEST_ATK`
- `ALL_ENEMIES`

Với BUFF/HEAL/SHIELD/IMMUNITY/DISPEL, engine chọn mục tiêu cùng đội dựa trên target mode. Với DAMAGE/DEBUFF/CC, engine chọn mục tiêu đối phương.

## Ý nghĩa tham số

- `multiplier`: hệ số theo công thức của từng effect handler.
- `chance`: xác suất kích hoạt trong khoảng 0 đến 1; bỏ trống nghĩa là 100%.
- `duration`: số round tồn tại của hiệu ứng. Các handler có thời hạn yêu cầu duration dương.
- `permanent`: chỉ áp dụng cho BUFF/DEBUFF; đặt `true` để modifier tồn tại đến khi bị giải hoặc trận đấu kết thúc. Khi đó `duration` phải bằng 0.
- `config_json`: JSON object. Các key enum dùng chữ hoa theo tên enum.

Với BUFF/DEBUFF thông thường, giá trị phần trăm hiện được tính bằng `multiplier * 100 + flatValue`. Ví dụ multiplier 0.20 tương ứng 20%.
