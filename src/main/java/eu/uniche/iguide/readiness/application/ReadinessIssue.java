package eu.uniche.iguide.readiness.application;

import java.util.UUID;

/**
 * One finding of the readiness evaluation ({@code 07} section 14): what it concerns, in which language, the field
 * path the CMS editors use, and the code.
 */
public record ReadinessIssue(String resourceType, UUID resourceId, String title, String languageCode, String path,
                             String code) {
}
