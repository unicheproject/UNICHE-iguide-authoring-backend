package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * {@code POST .../initialize} on a Project whose {@code initializedAt} is set ({@code 04} section 8, {@code 07}
 * sections 8 and 15): {@code 409}.
 */
public class ProjectAlreadyInitializedException extends ApiException {

    public ProjectAlreadyInitializedException() {
        super("The Project is already initialized.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "PROJECT_ALREADY_INITIALIZED";
    }
}
