package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.surface.domain.ProjectSurface;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.surface.infrastructure.ProjectSurfaceRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT .../surfaces} ({@code 03} section 2, {@code 12} section 4 Change surfaces): enables or disables each
 * listed surface and replaces its configuration; a surface left out keeps its row (DECISIONS.md D-029).
 *
 * <ul>
 *   <li>configuration keys are known per surface, unknown ones are refused ({@code 04} section 6): WEB takes
 *       {@code publicBaseUrl}, MOBILE and KIOSK nothing yet;
 *   <li>{@code publicBaseUrl} is an origin: {@code https}, or {@code http} for {@code localhost} only; no path, query,
 *       fragment, or user; stored without a trailing slash; blank removes it;
 *   <li>not yet: the Template's {@code supportedSurfaces} (no Templates before Milestone 2, GAPS.md G-035).
 * </ul>
 */
@Service
public class UpdateProjectSurfacesUseCase {

    private static final Map<Surface, Set<String>> KEYS = Map.of(
            Surface.WEB, Set.of(ProjectSurface.PUBLIC_BASE_URL),
            Surface.MOBILE, Set.of(),
            Surface.KIOSK, Set.of());
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final int MAX_URL = 2048;

    private final ProjectAccessGateway accessGateway;
    private final ProjectSurfaceRepository surfaces;
    private final SurfaceLookup lookup;
    private final TransactionTemplate transactionTemplate;

    public UpdateProjectSurfacesUseCase(ProjectAccessGateway accessGateway, ProjectSurfaceRepository surfaces,
                                        SurfaceLookup lookup, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.surfaces = surfaces;
        this.lookup = lookup;
        this.transactionTemplate = transactionTemplate;
    }

    public List<SurfaceView> execute(UUID projectId, UnichePrincipal principal, List<SurfaceInput> requested) {
        accessGateway.requireAccess(projectId, principal);
        List<SurfaceInput> inputs = requested == null ? List.of() : requested;
        List<ErrorItem> errors = new ArrayList<>();
        List<Map<String, Object>> configurations = new ArrayList<>();
        Set<Surface> seen = EnumSet.noneOf(Surface.class);
        for (int i = 0; i < inputs.size(); i++) {
            SurfaceInput input = inputs.get(i);
            String path = "surfaces[" + i + "]";
            if (input.surface() == null) {
                errors.add(new ErrorItem(path + ".surface", "REQUIRED", "Choose WEB, MOBILE, or KIOSK."));
                configurations.add(Map.of());
                continue;
            }
            if (!seen.add(input.surface())) {
                errors.add(new ErrorItem(path + ".surface", "DUPLICATE_VALUE", "The surface is listed twice."));
            }
            configurations.add(configuration(input, path + ".configuration", errors));
        }
        ResourceRules.throwIfAny(errors, "The surfaces are invalid.");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < inputs.size(); i++) {
                SurfaceInput input = inputs.get(i);
                ProjectSurface row = surfaces.findByProjectIdAndSurface(projectId, input.surface())
                        .orElseGet(() -> ProjectSurface.of(projectId, input.surface(), now));
                row.write(input.enabled(), configurations.get(i), now);
                surfaces.save(row);
            }
        });
        return lookup.all(projectId);
    }

    private static Map<String, Object> configuration(SurfaceInput input, String path, List<ErrorItem> errors) {
        Map<String, Object> requested = input.configuration() == null ? Map.of() : input.configuration();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : requested.entrySet()) {
            String key = entry.getKey();
            if (!KEYS.get(input.surface()).contains(key)) {
                errors.add(new ErrorItem(path + "." + key, "UNKNOWN_KEY",
                        "Is not an option of the " + input.surface() + " surface."));
            } else if (ProjectSurface.PUBLIC_BASE_URL.equals(key)) {
                String url = origin(entry.getValue(), path + "." + key, errors);
                if (url != null) {
                    result.put(key, url);
                }
            }
        }
        return result;
    }

    /** The normalized origin, {@code null} when blank or invalid (an error is added for the latter). */
    static String origin(Object value, String path, List<ErrorItem> errors) {
        if (value != null && !(value instanceof String)) {
            errors.add(new ErrorItem(path, "INVALID_URL", "Enter the address as text."));
            return null;
        }
        String text = ResourceRules.trimToNull((String) value);
        if (text == null) {
            return null;
        }
        String message = "Enter the origin the web app is hosted at, such as https://guide.museum.gr: https, or http "
                + "for localhost, with no path, query, or fragment.";
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            boolean secure = "https".equals(scheme) || ("http".equals(scheme) && host != null && LOCAL_HOSTS.contains(host));
            boolean bare = (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
            if (text.length() > MAX_URL || host == null || !secure || !bare) {
                errors.add(new ErrorItem(path, "INVALID_URL", message));
                return null;
            }
            return scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (URISyntaxException e) {
            errors.add(new ErrorItem(path, "INVALID_URL", message));
            return null;
        }
    }
}
