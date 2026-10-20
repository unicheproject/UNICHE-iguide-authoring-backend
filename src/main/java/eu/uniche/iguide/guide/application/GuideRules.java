package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.asset.application.InlineAssets;
import eu.uniche.iguide.asset.application.ResourceMedia;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.domain.RichText;
import eu.uniche.iguide.guide.infrastructure.GuideBlockRepository;
import eu.uniche.iguide.guide.infrastructure.GuideBlockTranslationRepository;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Guide use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class GuideRules {

    public static final int MAX_READING_MINUTES = 600;
    static final int MAX_NODES = 500;
    static final int MAX_NODE_TEXT = 20_000;

    private final GuideRepository guides;
    private final GuideTranslationRepository translations;
    private final GuideBlockRepository blocks;
    private final GuideBlockTranslationRepository blockTranslations;
    private final PointOfInterestRepository pois;
    private final RouteRepository routes;
    private final PlaceRepository places;
    private final EventRepository events;
    private final ProjectLanguageLookup languages;
    private final ResourceMedia media;
    private final InlineAssets inlineAssets;

    public GuideRules(GuideRepository guides, GuideTranslationRepository translations, GuideBlockRepository blocks,
                      GuideBlockTranslationRepository blockTranslations, PointOfInterestRepository pois,
                      RouteRepository routes, PlaceRepository places, EventRepository events,
                      ProjectLanguageLookup languages, ResourceMedia media, InlineAssets inlineAssets) {
        this.guides = guides;
        this.translations = translations;
        this.blocks = blocks;
        this.blockTranslations = blockTranslations;
        this.pois = pois;
        this.routes = routes;
        this.places = places;
        this.events = events;
        this.languages = languages;
        this.media = media;
        this.inlineAssets = inlineAssets;
    }

    public Guide requireGuide(UUID projectId, UUID guideId) {
        return guides.findByProjectIdAndId(projectId, guideId)
                .orElseThrow(() -> new ResourceNotFoundException("No such guide in this project."));
    }

    public GuideBlock requireBlock(UUID projectId, UUID guideId, UUID blockId) {
        return blocks.findByProjectIdAndGuideIdAndId(projectId, guideId, blockId)
                .orElseThrow(() -> new ResourceNotFoundException("No such block in this guide."));
    }

    public List<GuideBlock> blocksOf(UUID projectId, UUID guideId) {
        return blocks.findByProjectIdAndGuideIdOrderBySortOrderAsc(projectId, guideId);
    }

    public GuideView view(Guide guide) {
        return views(guide.getProjectId(), List.of(guide)).get(0);
    }

    /** Views of several Guides of one Project, loaded with one query per table. */
    public List<GuideView> views(UUID projectId, List<Guide> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Guide::getId).toList();
        Map<UUID, List<GuideTranslation>> texts = translations.findByProjectIdAndGuideIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(GuideTranslation::getGuideId));
        List<GuideBlock> all = blocks.findByProjectIdAndGuideIdInOrderBySortOrderAsc(projectId, ids);
        Map<UUID, List<GuideBlockTranslation>> blockTexts = all.isEmpty() ? Map.of()
                : blockTranslations.findByProjectIdAndGuideBlockIdIn(projectId,
                        all.stream().map(GuideBlock::getId).toList()).stream()
                        .collect(Collectors.groupingBy(GuideBlockTranslation::getGuideBlockId));
        Map<UUID, List<GuideBlock>> byGuide = all.stream().collect(Collectors.groupingBy(GuideBlock::getGuideId));
        Map<UUID, List<ResourceAsset>> mediaByGuide = media.of(projectId, AssetResourceType.GUIDE, ids);
        return list.stream().map(guide -> new GuideView(guide, texts.getOrDefault(guide.getId(), List.of()),
                byGuide.getOrDefault(guide.getId(), List.of()).stream()
                        .map(b -> new BlockView(b, blockTexts.getOrDefault(b.getId(), List.of())))
                        .toList(), mediaByGuide.getOrDefault(guide.getId(), List.of())))
                .toList();
    }

    public BlockView blockView(GuideBlock block) {
        return new BlockView(block, blockTranslations.findByProjectIdAndGuideBlockIdIn(block.getProjectId(),
                List.of(block.getId())));
    }

    /** Adds an error when the slug is malformed; throws {@code 409} when another Guide uses it. */
    public void checkSlug(UUID projectId, String slug, UUID ownId, List<ErrorItem> errors) {
        if (!ResourceRules.checkSlugFormat(slug, errors)) {
            return;
        }
        boolean taken = ownId == null
                ? guides.existsByProjectIdAndSlug(projectId, slug)
                : guides.existsByProjectIdAndSlugAndIdNot(projectId, slug, ownId);
        if (taken) {
            throw new DuplicateValueException("slug", "Another guide in this project already uses this slug.");
        }
    }

    public String generateSlug(UUID projectId, String title) {
        return SlugGenerator.generate(title, "guide", slug -> guides.existsByProjectIdAndSlug(projectId, slug));
    }

    public void checkLanguages(UUID projectId, Set<String> codes, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        codes.stream().filter(code -> !enabled.contains(code)).forEach(code -> errors.add(
                new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
    }

    /**
     * A block's shape: a TEXT block has no target, a REFERENCE block has one. The target must be a POI,
     * Route, Place, or Event of this Project that is not archived; Pages do not exist yet
     * (DECISIONS.md D-022). A target in another Project is simply unknown.
     */
    public void checkBlock(UUID projectId, GuideBlockType type, GuideTargetType targetType, UUID targetId,
                           List<ErrorItem> errors) {
        if (type == null) {
            errors.add(new ErrorItem("blockType", "REQUIRED", "Choose TEXT or REFERENCE."));
            return;
        }
        if (type == GuideBlockType.TEXT) {
            if (targetType != null || targetId != null) {
                errors.add(new ErrorItem("resourceType", "NOT_ALLOWED", "A TEXT block points at no resource."));
            }
            return;
        }
        if (targetType == null || targetId == null) {
            errors.add(new ErrorItem("resourceId", "REQUIRED", "A REFERENCE block needs a resource type and id."));
            return;
        }
        Optional<ResourceStatus> status = switch (targetType) {
            case POI -> pois.findByProjectIdAndId(projectId, targetId).map(PointOfInterest::getStatus);
            case ROUTE -> routes.findByProjectIdAndId(projectId, targetId).map(Route::getStatus);
            case PLACE -> places.findByProjectIdAndId(projectId, targetId).map(Place::getStatus);
            case EVENT -> events.findByProjectIdAndId(projectId, targetId).map(Event::getStatus);
            case PAGE -> null;
        };
        if (status == null) {
            errors.add(new ErrorItem("resourceType", "UNSUPPORTED_TYPE",
                    targetType + " resources cannot be referenced until they exist in iGuide."));
        } else if (status.isEmpty()) {
            errors.add(new ErrorItem("resourceId", "RESOURCE_NOT_FOUND", "No such resource in this project."));
        } else if (status.get() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("resourceId", "ARCHIVED_REFERENCE", "The resource is archived."));
        }
    }

    /**
     * Validates one language of a block and returns it normalized: rich text nodes trimmed, empty ones
     * dropped, an empty document null. {@code asset} nodes name an image of the Project ({@code 03} section 4).
     */
    public BlockTranslationInput normalize(UUID projectId, GuideBlockType type, String code,
                                           BlockTranslationInput input, List<ErrorItem> errors) {
        String path = "translations." + code;
        if (type == GuideBlockType.REFERENCE) {
            if (input.text() != null && input.text().blocks() != null && !input.text().blocks().isEmpty()) {
                errors.add(new ErrorItem(path + ".text", "NOT_ALLOWED", "A REFERENCE block has a caption, not text."));
            }
            return new BlockTranslationInput(null, ResourceRules.trimToNull(input.caption()));
        }
        if (ResourceRules.trimToNull(input.caption()) != null) {
            errors.add(new ErrorItem(path + ".caption", "NOT_ALLOWED", "A TEXT block has text, not a caption."));
        }
        return new BlockTranslationInput(normalizeText(projectId, input.text(), path + ".text", errors), null);
    }

    RichText normalizeText(UUID projectId, RichText text, String path, List<ErrorItem> errors) {
        if (text == null || text.blocks() == null) {
            return null;
        }
        if (text.blocks().size() > MAX_NODES) {
            errors.add(new ErrorItem(path + ".blocks", "TOO_LONG", "At most " + MAX_NODES + " blocks."));
            return null;
        }
        List<RichText.Node> kept = new ArrayList<>();
        for (int i = 0; i < text.blocks().size(); i++) {
            RichText.Node node = text.blocks().get(i);
            String nodePath = path + ".blocks[" + i + "]";
            String type = node == null ? null : node.type();
            if (RichText.ASSET.equals(type)) {
                int before = errors.size();
                inlineAssets.check(projectId, node.assetId(), nodePath + ".assetId", errors);
                if (errors.size() == before) {
                    kept.add(new RichText.Node(RichText.ASSET, null, null, node.assetId().strip().toLowerCase()));
                }
                continue;
            }
            if (!"paragraph".equals(type) && !"heading".equals(type)) {
                errors.add(new ErrorItem(nodePath + ".type", "INVALID", "Must be paragraph, heading, or asset."));
                continue;
            }
            String body = ResourceRules.trimToNull(node.text());
            if (body != null && body.length() > MAX_NODE_TEXT) {
                errors.add(new ErrorItem(nodePath + ".text", "TOO_LONG", "At most " + MAX_NODE_TEXT + " characters."));
                continue;
            }
            Integer level = null;
            if ("heading".equals(type)) {
                if (node.level() == null || (node.level() != 2 && node.level() != 3)) {
                    errors.add(new ErrorItem(nodePath + ".level", "INVALID", "A heading level is 2 or 3."));
                    continue;
                }
                level = node.level();
            }
            if (body != null) {
                kept.add(new RichText.Node(type, body, level, null));
            }
        }
        return kept.isEmpty() ? null : new RichText(kept);
    }

    /**
     * Makes the Guide's {@code INLINE} bindings the Assets embedded in the text of its TEXT blocks, in every
     * language ({@code 07} section 10). Called after every block write.
     */
    public void reconcileInlineAssets(UUID projectId, UUID guideId) {
        List<UUID> blockIds = blocksOf(projectId, guideId).stream().map(GuideBlock::getId).toList();
        java.util.Set<UUID> embedded = new java.util.HashSet<>();
        if (!blockIds.isEmpty()) {
            blockTranslations.findByProjectIdAndGuideBlockIdIn(projectId, blockIds).stream()
                    .map(GuideBlockTranslation::getText)
                    .filter(text -> text != null && text.blocks() != null)
                    .flatMap(text -> text.blocks().stream())
                    .filter(node -> node != null && RichText.ASSET.equals(node.type()) && node.assetId() != null)
                    .forEach(node -> embedded.add(UUID.fromString(node.assetId())));
        }
        inlineAssets.reconcile(projectId, AssetResourceType.GUIDE, guideId, embedded);
    }

    /** Writes the given languages of a block; languages left out keep their text. */
    public void saveBlockTranslations(GuideBlock block, Map<String, BlockTranslationInput> normalized) {
        if (normalized.isEmpty()) {
            return;
        }
        Map<String, GuideBlockTranslation> existing = blockTranslations
                .findByProjectIdAndGuideBlockIdIn(block.getProjectId(), List.of(block.getId())).stream()
                .collect(Collectors.toMap(GuideBlockTranslation::getLanguageCode, Function.identity()));
        List<GuideBlockTranslation> toSave = new ArrayList<>();
        normalized.forEach((code, input) -> {
            GuideBlockTranslation row = existing.getOrDefault(code,
                    GuideBlockTranslation.of(block.getProjectId(), block.getId(), code));
            row.write(input.text(), input.caption());
            toSave.add(row);
        });
        blockTranslations.saveAll(toSave);
    }

    /**
     * Non-archived Guides with a REFERENCE block on the Resource, titled in the default language or by
     * slug ({@code 03} section 1: {@code GuideBlock} rows are references).
     */
    public List<ResourceReference> referencesTo(UUID projectId, GuideTargetType type, UUID resourceId) {
        List<Guide> using = guides.findReferencing(projectId, type, resourceId);
        if (using.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndGuideIdIn(projectId,
                        using.stream().map(Guide::getId).toList()).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(GuideTranslation::getGuideId, GuideTranslation::getTitle));
        return using.stream()
                .map(g -> new ResourceReference("GUIDE", g.getId(), titles.getOrDefault(g.getId(), g.getSlug()), g.getStatus()))
                .toList();
    }
}
