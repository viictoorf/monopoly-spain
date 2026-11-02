package monopoly.game;

/** The eight colour groups of streets. */
public enum ColorGroup {
    BROWN("#8b5a2b"), LIGHT_BLUE("#7ec8e3"), PINK("#d94fa0"), ORANGE("#f28c28"),
    RED("#d62828"), YELLOW("#f4d03f"), GREEN("#2e9e5b"), DARK_BLUE("#1f4e9e");

    private final String hex;

    ColorGroup(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }
}
