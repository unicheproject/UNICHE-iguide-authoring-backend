package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The theme and the settings exist only once a Template has initialized the Project ({@code 04} section 8):
 * {@code 409} until then, so the CMS can offer "resume initialization" (DECISIONS.md D-040).
 */
public class ProjectNotInitializedException extends ApiException {

    public ProjectNotInitializedException() {
        super("The Project is not initialized yet; initialize it from a Template first.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "PROJECT_NOT_INITIALIZED";
    }
}
