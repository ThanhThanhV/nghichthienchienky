package vn.nghichtien.chienky.hero.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.nghichtien.chienky.battle.domain.BattleEffectType;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.hero.domain.HeroBaseStats;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroGrowthConfig;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.dto.CreateHeroRequest;
import vn.nghichtien.chienky.hero.dto.HeroManagementCreatedResponse;
import vn.nghichtien.chienky.hero.repository.HeroBaseStatsRepository;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroGrowthConfigRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tạo định nghĩa tướng, lưu chỉ số/kỹ năng vào database và bộ Spine vào assets.
 */
@Service
public class HeroManagementService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024;
    private static final int MAX_TEXTURES = 20;
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,119}");

    private static final List<String> SKILL_TYPES = List.of(
            "BASIC", "ACTIVE", "ULTIMATE", "PASSIVE", "PASSIVE"
    );

    private static final Set<String> RARITIES = Set.of(
            "COMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
    );

    private static final Set<String> ROLES = Set.of(
            "WARRIOR", "ASSASSIN", "MAGE", "TANK", "SUPPORT"
    );

    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBaseStatsRepository heroBaseStatsRepository;
    private final HeroGrowthConfigRepository heroGrowthConfigRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final Path assetRoot;

    public HeroManagementService(
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            ObjectMapper objectMapper,
            @Value("${game.assets.spine-heroes-path:src/main/resources/static/assets/spine/heroes}")
            String assetPath
    ) {
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBaseStatsRepository = heroBaseStatsRepository;
        this.heroGrowthConfigRepository = heroGrowthConfigRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.objectMapper = objectMapper;
        this.assetRoot = Path.of(assetPath).toAbsolutePath().normalize();
    }

    /**
     * Lưu mới một tướng cùng bộ tài nguyên Spine đã được kiểm tra.
     */
    @Transactional
    public HeroManagementCreatedResponse create(
            CreateHeroRequest request,
            MultipartFile skeletonFile,
            MultipartFile atlasFile,
            List<MultipartFile> textureFiles
    ) {
        String heroCode = request.heroCode().trim().toLowerCase(Locale.ROOT);
        validateBusinessFields(request, heroCode);

        if (heroDefinitionRepository.existsByHeroCode(heroCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Mã tướng đã tồn tại: " + heroCode
            );
        }

        SpineUpload spineUpload = validateSpineFiles(
                skeletonFile, atlasFile, textureFiles
        );
        Set<String> availableAnimations = Set.copyOf(spineUpload.animations());
        for (int i = 0; i < request.skills().size(); i++) {
            if (!availableAnimations.contains(request.skills().get(i).animation())) {
                throw badRequest("Không tìm thấy animation đã chọn ở kỹ năng " + (i + 1)
                        + ": " + request.skills().get(i).animation());
            }
        }

        Path heroDirectory = assetRoot.resolve(heroCode).normalize();
        if (!heroDirectory.startsWith(assetRoot) || Files.exists(heroDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Thư mục assets của mã tướng đã tồn tại"
            );
        }

        List<String> textureUrls = new ArrayList<>();
        String assetUrl = "/assets/spine/heroes/" + heroCode + "/";

        try {
            Files.createDirectories(assetRoot);
            Files.createDirectory(heroDirectory);

            copyFile(spineUpload.skeletonBytes(), heroDirectory.resolve(spineUpload.skeletonName()));
            copyFile(spineUpload.atlasBytes(), heroDirectory.resolve(spineUpload.atlasName()));

            for (TextureUpload texture : spineUpload.textures()) {
                copyFile(texture.bytes(), heroDirectory.resolve(texture.fileName()));
                textureUrls.add(assetUrl + texture.fileName());
            }

            String skeletonUrl = assetUrl + spineUpload.skeletonName();
            String atlasUrl = assetUrl + spineUpload.atlasName();

            HeroDefinition definition = new HeroDefinition(
                    heroCode,
                    request.heroName().trim(),
                    request.rarity().trim().toUpperCase(Locale.ROOT),
                    request.role().trim().toUpperCase(Locale.ROOT),
                    request.avatarCode().trim()
            );
            definition.updateDescription(request.description().trim());
            definition.updateFaction(request.faction().trim());
            definition.updateSpineAssets(
                    skeletonUrl,
                    atlasUrl,
                    objectMapper.writeValueAsString(textureUrls)
            );
            definition.changeSortOrder((int) heroDefinitionRepository.count() + 1);
            heroDefinitionRepository.save(definition);

            saveBaseStats(definition, request.baseStats());
            saveGrowthStats(definition, request.growthStats());
            saveSkills(definition, request.skills());

            // Flush trong transaction để các lỗi constraint được phát hiện trước khi giữ lại file.
            heroDefinitionRepository.flush();
            heroBaseStatsRepository.flush();
            heroGrowthConfigRepository.flush();
            heroSkillDefinitionRepository.flush();

            return new HeroManagementCreatedResponse(
                    heroCode,
                    definition.getHeroName(),
                    skeletonUrl,
                    atlasUrl,
                    List.copyOf(textureUrls),
                    spineUpload.animations()
            );
        } catch (Exception exception) {
            deleteDirectory(heroDirectory);
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu tướng và tài nguyên Spine: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateBusinessFields(CreateHeroRequest request, String heroCode) {
        if (!RARITIES.contains(request.rarity().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Độ hiếm không được hỗ trợ: " + request.rarity());
        }
        if (!ROLES.contains(request.role().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Vai trò không được hỗ trợ: " + request.role());
        }
        if (heroCode.length() > 50) {
            throw badRequest("Mã tướng tối đa 50 ký tự");
        }

        if (request.skills() == null || request.skills().size() != 5) {
            throw badRequest("Tướng phải có đúng 5 kỹ năng");
        }

        for (int i = 0; i < request.skills().size(); i++) {
            CreateHeroRequest.SkillRequest skill = request.skills().get(i);
            String expectedType = SKILL_TYPES.get(i);
            try {
                BattleEffectType.valueOf(skill.effectType().trim().toUpperCase(Locale.ROOT));
                BattleTargetMode.valueOf(skill.targetType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw badRequest("Loại hiệu ứng hoặc mục tiêu không hợp lệ ở kỹ năng " + (i + 1));
            }

            if (skill.animationDurationMs() < 1
                    || skill.impactTimeMs() > skill.animationDurationMs()) {
                throw badRequest("Thời điểm tác động phải nằm trong thời lượng animation ở kỹ năng " + (i + 1));
            }
            if (("DOT".equalsIgnoreCase(skill.effectType())
                    || "HOT".equalsIgnoreCase(skill.effectType()))
                    && skill.duration() <= 0) {
                throw badRequest("DOT/HOT cần duration lớn hơn 0 ở kỹ năng " + (i + 1));
            }
            if (skill.chance().compareTo(BigDecimal.ONE) > 0) {
                throw badRequest("Chance không được lớn hơn 1 ở kỹ năng " + (i + 1));
            }
            if (expectedType.isBlank()) {
                throw badRequest("Loại kỹ năng không hợp lệ");
            }
        }
    }

    private SpineUpload validateSpineFiles(
            MultipartFile skeletonFile,
            MultipartFile atlasFile,
            List<MultipartFile> textureFiles
    ) {
        if (skeletonFile == null || skeletonFile.isEmpty()
                || atlasFile == null || atlasFile.isEmpty()
                || textureFiles == null || textureFiles.isEmpty()) {
            throw badRequest("Cần upload Skeleton JSON, Atlas và ít nhất một PNG");
        }
        if (textureFiles.size() > MAX_TEXTURES) {
            throw badRequest("Tối đa " + MAX_TEXTURES + " texture PNG");
        }

        try {
            long totalSize = skeletonFile.getSize() + atlasFile.getSize()
                    + textureFiles.stream().mapToLong(MultipartFile::getSize).sum();
            if (totalSize > MAX_TOTAL_SIZE) {
                throw badRequest("Tổng dung lượng bộ Spine không được vượt quá 50 MB");
            }

            String skeletonName = safeFileName(skeletonFile.getOriginalFilename(), ".json");
            String atlasName = safeFileName(atlasFile.getOriginalFilename(), ".atlas", ".txt");
            byte[] skeletonBytes = readFile(skeletonFile);
            byte[] atlasBytes = readFile(atlasFile);

            JsonNode skeletonJson = objectMapper.readTree(skeletonBytes);
            if (skeletonJson == null
                    || !skeletonJson.path("skeleton").isObject()
                    || !skeletonJson.path("bones").isArray()
                    || !skeletonJson.path("animations").isObject()
                    || skeletonJson.path("animations").isEmpty()) {
                throw badRequest("Skeleton JSON không đúng cấu trúc Spine hoặc không có animation");
            }

            String atlasText = new String(atlasBytes, java.nio.charset.StandardCharsets.UTF_8);
            Set<String> atlasPages = extractAtlasPages(atlasText);
            if (atlasPages.isEmpty()) {
                throw badRequest("Không tìm thấy texture page trong Atlas");
            }

            Set<String> uploadedTextureNames = new HashSet<>();
            List<TextureUpload> textures = new ArrayList<>();
            for (MultipartFile textureFile : textureFiles) {
                String textureName = safeFileName(textureFile.getOriginalFilename(), ".png");
                String normalizedName = textureName.toLowerCase(Locale.ROOT);
                if (!uploadedTextureNames.add(normalizedName)) {
                    throw badRequest("Tên texture bị trùng: " + textureName);
                }
                byte[] bytes = readFile(textureFile);
                validatePng(bytes, textureName);
                textures.add(new TextureUpload(textureName, bytes));
            }

            Set<String> missingTextures = new HashSet<>();
            for (String page : atlasPages) {
                if (!uploadedTextureNames.contains(page.toLowerCase(Locale.ROOT))) {
                    missingTextures.add(page);
                }
            }
            if (!missingTextures.isEmpty()) {
                throw badRequest("Thiếu texture được Atlas tham chiếu: " + String.join(", ", missingTextures));
            }

            List<String> animations = new ArrayList<>();
            skeletonJson.path("animations").fieldNames().forEachRemaining(animations::add);
            return new SpineUpload(
                    skeletonName, atlasName, skeletonBytes, atlasBytes,
                    List.copyOf(textures), List.copyOf(animations)
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw badRequest("Không thể đọc file Spine: " + exception.getMessage());
        }
    }

    private Set<String> extractAtlasPages(String atlasText) {
        Set<String> pages = new HashSet<>();
        for (String line : atlasText.split("\\R")) {
            String value = line.trim();
            if (value.toLowerCase(Locale.ROOT).endsWith(".png")) {
                String fileName = value.replace('\\', '/');
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
                pages.add(fileName);
            }
        }
        return pages;
    }

    private String safeFileName(String originalName, String... extensions) {
        if (originalName == null || originalName.isBlank()) {
            throw badRequest("File upload thiếu tên");
        }
        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw badRequest("Tên file không hợp lệ: " + fileName);
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean validExtension = false;
        for (String extension : extensions) {
            if (lowerName.endsWith(extension)) {
                validExtension = true;
                break;
            }
        }
        if (!validExtension) {
            throw badRequest("Phần mở rộng file không hợp lệ: " + fileName);
        }
        return fileName;
    }

    private byte[] readFile(MultipartFile file) throws IOException {
        if (file.getSize() <= 0 || file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("Mỗi file phải có dung lượng từ 1 byte đến 20 MB");
        }
        return file.getBytes();
    }

    private void validatePng(byte[] bytes, String fileName) {
        byte[] signature = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        if (bytes.length < signature.length) {
            throw badRequest("PNG không hợp lệ: " + fileName);
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                throw badRequest("Nội dung file không phải PNG hợp lệ: " + fileName);
            }
        }
    }

    private void copyFile(byte[] bytes, Path destination) throws IOException {
        Files.copy(new ByteArrayInputStream(bytes), destination);
    }

    private void saveBaseStats(
            HeroDefinition definition,
            CreateHeroRequest.BaseStatsRequest stats
    ) {
        HeroBaseStats entity = new HeroBaseStats(
                definition, stats.hp(), stats.atk(), stats.def(), stats.speed()
        );
        entity.updateStats(
                stats.hp(), stats.atk(), stats.def(), stats.speed(),
                stats.crit(), stats.critDamage(), stats.accuracy(),
                stats.resistance(), stats.penFlat(), stats.penPercent(),
                stats.lifesteal()
        );
        heroBaseStatsRepository.save(entity);
    }

    private void saveGrowthStats(
            HeroDefinition definition,
            CreateHeroRequest.GrowthStatsRequest stats
    ) {
        HeroGrowthConfig entity = new HeroGrowthConfig(definition);
        entity.updateGrowth(
                stats.hp(), stats.atk(), stats.def(), stats.speed(),
                stats.crit(), stats.critDamage(), stats.accuracy(),
                stats.resistance(), stats.penFlat(), stats.penPercent(),
                stats.lifesteal()
        );
        heroGrowthConfigRepository.save(entity);
    }

    private void saveSkills(
            HeroDefinition definition,
            List<CreateHeroRequest.SkillRequest> skillRequests
    ) throws IOException {
        List<HeroSkillDefinition> skills = new ArrayList<>();
        for (int i = 0; i < skillRequests.size(); i++) {
            int order = i + 1;
            String skillType = SKILL_TYPES.get(i);
            CreateHeroRequest.SkillRequest request = skillRequests.get(i);

            String skillCode = definition.getHeroCode().toUpperCase(Locale.ROOT)
                    .substring(0, Math.min(definition.getHeroCode().length(), 30))
                    + "_SKILL_" + order;

            HeroSkillDefinition skill = new HeroSkillDefinition(
                    definition, skillCode, request.name().trim(), skillType, order
            );
            skill.updateInformation(request.name().trim(), request.description().trim(), null);

            HeroSkillEffect effect = new HeroSkillEffect(
                    1,
                    request.effectType().trim().toUpperCase(Locale.ROOT),
                    request.targetType().trim().toUpperCase(Locale.ROOT)
            );
            effect.updateParameters(
                    request.multiplier(), request.chance(), request.duration()
            );

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("animation", request.animation().trim());
            config.put("animationDurationMs", request.animationDurationMs());
            config.put("impactTimeMs", request.impactTimeMs());
            config.put("flatValue", BigDecimal.ZERO);
            if ("PASSIVE".equals(skillType)) {
                config.put("trigger", "BATTLE_START");
            }
            effect.updateConfig(objectMapper.writeValueAsString(config));
            skill.addEffect(effect);
            skills.add(skill);
        }
        heroSkillDefinitionRepository.saveAll(skills);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup after a failed creation.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup after a failed creation.
        }
    }

    private record TextureUpload(String fileName, byte[] bytes) {
    }

    private record SpineUpload(
            String skeletonName,
            String atlasName,
            byte[] skeletonBytes,
            byte[] atlasBytes,
            List<TextureUpload> textures,
            List<String> animations
    ) {
    }
}
