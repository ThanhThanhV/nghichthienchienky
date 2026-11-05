# Cấu hình kỹ năng chiến đấu

## Kiến trúc

- `hero_skill_definitions`: định nghĩa kỹ năng theo nhân vật.
- `hero_skill_effects`: các hiệu ứng theo thứ tự thực hiện.
- `DatabaseSkillExecutor`: tải kỹ năng, kiểm tra xác suất, chuyển dữ liệu DB thành cấu hình runtime.
- `BattleEffectProcessor`: thực thi hiệu ứng bằng luật Java hiện có.
- `BattleTurnProcessor`: tự động gọi nội tại theo vòng đời và sự kiện chiến đấu.

Mỗi nhân vật cần có ít nhất một kỹ năng `BASIC`. Chu kỳ lượt hiện tại là `ACTIVE -> ULTIMATE -> BASIC`. Nếu thiếu kỹ năng ACTIVE hoặc ULTIMATE, hành động tương ứng được bỏ qua; engine không tự đổi một kỹ năng khác thành BASIC. SILENCE chuyển hành động hiện tại sang BASIC. Gojo hiện có một ACTIVE và ba PASSIVE theo cách phân loại runtime đã thống nhất, nên không có lượt ULTIMATE giả.

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
| `ON_DAMAGED` | Khi nhân vật sở hữu nội tại nhận sát thương HP thực tế và còn sống |
| `ON_ENEMY_ACTION` | Sau mỗi hành động thực sự được phe đối phương thực hiện (đánh, buff, debuff, hồi máu...); không kích hoạt khi bị khóa hoặc bỏ lượt |
| `ON_REVIVE` | Ngay sau khi một đơn vị phe đối phương hồi sinh thành công; event giữ đúng instance vừa hồi sinh làm mục tiêu |
| `ON_KILL` | Khi nhân vật sở hữu nội tại hạ gục mục tiêu, kể cả mục tiêu bị hạ bởi counter reaction được engine chuyển tiếp |

Các trigger đều do Java tự kiểm tra; client không gửi yêu cầu kích hoạt. Passive reaction không tự kích hoạt dây chuyền `ON_ENEMY_ACTION` hoặc `ON_REVIVE` lần nữa. Riêng event `DEATH` phát sinh trực tiếp từ counter reaction được chuyển tiếp một lần sang `ON_KILL`, để các nội tại cộng dồn khi hạ gục (như Lục Nhãn của Gojo) vẫn nhận đúng kết quả mà không tạo vòng lặp phản kích.

## Target type

- `SELF`: chỉ chính nhân vật kích hoạt.
- `RANDOM`: một mục tiêu ngẫu nhiên.
- `FRONT`: hàng trước.
- `FRONT_AND_MIDDLE`: hai hàng đầu tiên (hàng trước và hàng giữa).
- `BACK`: hàng sau.
- `LOWEST_HP_BACK`: đồng minh có HP thấp nhất trong hàng sau.
- `RANDOM_2`, `RANDOM_3`: chọn ngẫu nhiên tối đa 2 hoặc 3 mục tiêu.
- `LOWEST_HP`: mục tiêu có HP hiện tại thấp nhất.
- `HIGHEST_ATK`: mục tiêu có ATK cao nhất.
- `HIGHEST_ATK_3`: tối đa 3 mục tiêu có ATK cao nhất.
- `MOST_POPULATED_ROW`: hàng có nhiều đơn vị còn sống nhất; nếu hòa chọn hàng trước.
- `ALL_ENEMIES`: toàn bộ đối thủ (với BUFF/HEAL/SHIELD, engine áp dụng cho toàn bộ đồng minh).

Với BUFF/HEAL/SHIELD/IMMUNITY/DISPEL, engine chọn mục tiêu cùng đội. Với DAMAGE/DEBUFF/CC, engine chọn mục tiêu đối phương. `SELF` luôn chọn chính nhân vật sở hữu nội tại.

## Ví dụ: nội tại tăng 20% ATK vĩnh viễn khi vào trận

- `skill_type = PASSIVE`
- `effect_type = BUFF`
- `target_type = SELF`
- `multiplier = 0.20`
- `chance = 1`
- `duration = 0`
- `config_json = {"trigger":"BATTLE_START","statType":"ATK","permanent":true}`

## Ví dụ: nội tại có 30% cơ hội tăng 15% ATK trong 2 round sau mỗi lần đánh

- `skill_type = PASSIVE`
- `effect_type = BUFF`
- `target_type = SELF`
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

| COUNTER | Phản công đúng đơn vị vừa gây sát thương | PASSIVE với `trigger: ON_DAMAGED`; multiplier là hệ số sát thương |
| EXTRA_ACTION | Cấp thêm một hành động | Không cần config riêng; tối đa 1 lần mỗi đơn vị mỗi round |

Các enum hợp lệ được định nghĩa trong `BattleEffectType`, `BattleTargetMode`, `BattleStatType`, `BattleStatusType`, `BattleImmunityType` và `BattleDispelType`.

## EXTRA_ACTION

- Có thể đặt trong kỹ năng thường hoặc PASSIVE.
- Khi được kích hoạt, nhân vật thực hiện thêm một hành động ngay sau khi xử lý xong sự kiện đang kích hoạt nó.
- Hành động bổ sung tiếp tục chu kỳ `ACTIVE -> ULTIMATE -> BASIC`.
- Mỗi nhân vật tối đa một EXTRA_ACTION trong một round; hiệu ứng kích hoạt thêm trong hành động bổ sung không tạo chuỗi vô hạn.
- `target_type` nên đặt là `SELF`; `multiplier` và `flatValue` không được sử dụng.

## Ý nghĩa tham số

- `multiplier`: hệ số theo công thức của từng effect handler.
- `chance`: xác suất kích hoạt trong khoảng 0 đến 1; bỏ trống nghĩa là 100%.
- `duration`: số round tồn tại của hiệu ứng. Các handler có thời hạn yêu cầu duration dương.
- `permanent`: chỉ áp dụng cho BUFF/DEBUFF; đặt `true` để modifier tồn tại đến khi bị giải hoặc trận đấu kết thúc. Khi đó `duration` phải bằng 0.
- `config_json`: JSON object. Các key enum dùng chữ hoa theo tên enum.

Với BUFF/DEBUFF thông thường, giá trị phần trăm hiện được tính bằng `multiplier * 100 + flatValue`. Ví dụ multiplier 0.20 tương ứng 20%.
