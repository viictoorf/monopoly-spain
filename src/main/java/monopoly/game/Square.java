package monopoly.game;

/**
 * Base class of every board square. Each subclass decides what happens when a player lands on it:
 * the engine just calls onLand() and polymorphism does the rest.
 */
public abstract class Square {
    private final int index;
    private final String name;
    private String shortName;
    private String blurb = "";

    protected Square(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public int getIndex() { return index; }
    public String getName() { return name; }
    /** Compact name for small screens. */
    public String getShortName() { return shortName == null ? name : shortName; }
    /** A one-line wink shown in the square's info card. */
    public String getBlurb() { return blurb; }

    /** Fluent setter used by Board to attach the flavour text. */
    Square info(String shortName, String blurb) {
        this.shortName = shortName;
        this.blurb = blurb;
        return this;
    }

    /** Short identifier for the UI ("street", "tax", ...). */
    public abstract String getKind();

    abstract void onLand(Game game, Player player);
}
