package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.infrastructure.GuideBlockRepository;
import eu.uniche.iguide.guide.infrastructure.GuideBlockTranslationRepository;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.readiness.application.Texts;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Readiness of ACTIVE Guides ({@code 03} section 1, section 11; DECISIONS.md D-033): the title, at least one block, and
 * text on every TEXT block in the default language; translations of the title, summary, and story (the first TEXT
 * block, as the CMS edits it); reference blocks whose target is gone or archived.
 */
@Component
public class GuideReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("title", "summary");

    private final GuideRepository guides;
    private final GuideTranslationRepository translations;
    private final GuideBlockRepository blocks;
    private final GuideBlockTranslationRepository blockTranslations;

    public GuideReadiness(GuideRepository guides, GuideTranslationRepository translations, GuideBlockRepository blocks,
                          GuideBlockTranslationRepository blockTranslations) {
        this.guides = guides;
        this.translations = translations;
        this.blocks = blocks;
        this.blockTranslations = blockTranslations;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<UUID> active = guides.findByProjectId(context.projectId()).stream()
                .filter(g -> g.getStatus() == ResourceStatus.ACTIVE).map(Guide::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndGuideIdIn(context.projectId(), active), GuideTranslation::getGuideId,
                GuideTranslation::getLanguageCode, t -> Texts.of("title", t.getTitle(), "summary", t.getSummary()));
        List<GuideBlock> allBlocks = blocks.findByProjectIdAndGuideIdInOrderBySortOrderAsc(context.projectId(), active);
        Map<UUID, List<GuideBlock>> byGuide = allBlocks.stream().collect(Collectors.groupingBy(GuideBlock::getGuideId));
        Map<UUID, Set<String>> textedLanguages = allBlocks.isEmpty() ? Map.of()
                : blockTranslations.findByProjectIdAndGuideBlockIdIn(context.projectId(),
                                allBlocks.stream().map(GuideBlock::getId).toList()).stream()
                        .filter(GuideBlockTranslation::hasText)
                        .collect(Collectors.groupingBy(GuideBlockTranslation::getGuideBlockId,
                                Collectors.mapping(GuideBlockTranslation::getLanguageCode, Collectors.toSet())));
        String language = context.defaultLanguage();
        for (UUID id : active) {
            String title = context.index().title("GUIDE", id);
            report.texts(context, "GUIDE", id, title, texts.getOrDefault(id, Map.of()), FIELDS, "title", List.of());
            List<GuideBlock> own = byGuide.getOrDefault(id, List.of());
            if (own.isEmpty() && language != null) {
                report.block("GUIDE", id, title, language, "blocks", "MISSING_DEFAULT_LANGUAGE_VALUE");
            }
            boolean story = true;
            for (int i = 0; i < own.size(); i++) {
                GuideBlock block = own.get(i);
                if (block.getBlockType() == GuideBlockType.TEXT) {
                    Set<String> texted = textedLanguages.getOrDefault(block.getId(), Set.of());
                    String path = "blocks[" + i + "].translations.";
                    if (language != null && !texted.contains(language)) {
                        report.block("GUIDE", id, title, language, path + language + ".text",
                                "MISSING_DEFAULT_LANGUAGE_VALUE");
                    } else if (story && language != null) {
                        for (String other : context.otherLanguages()) {
                            if (!texted.contains(other)) {
                                report.warn("GUIDE", id, title, other, path + other + ".text", "MISSING_TRANSLATION");
                            }
                        }
                    }
                    story = false;
                } else if (block.getResourceType() != null && block.getResourceType() != GuideTargetType.PAGE
                        && context.index().dangling(block.getResourceType().name(), block.getResourceId())) {
                    report.block("GUIDE", id, title, null, "blocks[" + i + "].resourceId", "DANGLING_REFERENCE");
                }
            }
        }
    }
}
