package eu.uniche.iguide.asset.domain;

/** What an Asset holds ({@code 03} section 4). A {@code FLOOR_PLAN} is an image uploaded as a plan. */
public enum AssetType {
    IMAGE, AUDIO, VIDEO, DOCUMENT, THREE_D_MODEL, FLOOR_PLAN, OTHER;

    public boolean isImage() {
        return this == IMAGE || this == FLOOR_PLAN;
    }

    /** "image", "3D model": for messages. */
    public String label() {
        return switch (this) {
            case IMAGE -> "image";
            case AUDIO -> "audio";
            case VIDEO -> "video";
            case DOCUMENT -> "document";
            case THREE_D_MODEL -> "3D model";
            case FLOOR_PLAN -> "floor plan";
            case OTHER -> "other";
        };
    }
}
