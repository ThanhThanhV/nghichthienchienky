# Cấu hình kỹ năng chiến đấu

## Kiến trúc

- `hero_skill_definitions`: định nghĩa kỹ năng theo nhân vật.
- `hero_skill_effects`: các hiệu ứng theo thứ tự thực hiện.
- `DatabaseSkillExecutor`: tải kỹ năng, kiểm tra xác suất, chuyển dữ liệu DB thành cấu hình runtime.
- `BattleEffectProcessor`: thực thi hiệu ứng bằng luật Java hiện có.

Mỗi nhân vật cần có ít nhất một kỹ năng `BASIC`. Chu kỳ lượt hiện tại là `ACTIVE -> ULTIMATE -> BASIC`. Nếu thiếu kỹ năng ACTIVE hoặc ULTIMATE, lượt đó tự động dùng BASIC. SILENCE cũng chuyển hành động sang BASIC.

## Loại kỹ năng

| skill_type | Ý nghĩa |
|---|---|
| BASIC | Đánh thường |
| ACTIVE | Kỹ năng chủ động |
| ULTIMATE | Tuyệt kỹ |
| PASSIVE | Nội tại; chưa được kích hoạt tự động trong lượt hiện tại |

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

Các enum hợp lệ được định nghĩa trong `BattleEffectType`, `BattleTargetMode`, `BattleStatType`, `BattleStatusType`, `BattleImmunityType` và `BattleDispelType`. Không tự thêm chuỗi ngoài enum.

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
- `chance`: xác suất kích hoạt trong khoảng 0 đến 1; bỏ trống nghĩa là 100%. Hiện xác suất được quay một lần cho mỗi effect trong mỗi lần dùng skill.
- `duration`: số lượt tồn tại của hiệu ứng trạng thái; các handler BUFF/DEBUFF/CC/SHIELD/EVASION/BLOCK/REFLECT/IMMUNITY yêu cầu duration dương.
- `config_json`: JSON object. Các key enum dùng chữ hoa theo tên enum.

### Ví dụ cấu hình

Kỹ năng đánh toàn bộ đối thủ, hệ số 1.5:

- `skill_type = ACTIVE`
- `effect_type = DAMAGE`
- `target_type = ALL_ENEMIES`
- `multiplier = 1.5`
- `chance = 1`
- `duration = 0`
- `config_json = {"flatValue": 0}`

Kỹ năng giảm DEF một mục tiêu trong 2 lượt, xác suất 75%:

- `effect_type = DEBUFF`
- `target_type = RANDOM`
- `multiplier = 0.20`
- `chance = 0.75`
- `duration = 2`
- `config_json = {"statType": "DEF"}`

Lưu ý: `multiplier` của BUFF/DEBUFF hiện được BattleEffectProcessor quy đổi thành phần trăm bằng `multiplier * 100 + flatValue`. Ví dụ 0.20 tương ứng 20%.
