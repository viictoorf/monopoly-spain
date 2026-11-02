package monopoly.game;

/**
 * A city street. houses: 0-4 houses, 5 = hotel. With the house rules a hotel can also get a pool and then a
 * sports court (each adds 25x the base rent, as in the original coursework version).
 */
public final class Street extends Property {
    private final ColorGroup group;
    private final int[] rents;      // [base, 1 house, 2, 3, 4, hotel]
    private final int houseCost;
    private int houses;
    private boolean pool;
    private boolean court;

    Street(int index, String name, ColorGroup group, int price, int houseCost, int... rents) {
        super(index, name, price);
        this.group = group;
        this.houseCost = houseCost;
        this.rents = rents.clone();
    }

    public ColorGroup getGroup() { return group; }
    public int getHouseCost() { return houseCost; }
    public int getPoolCost() { return getPrice() * 2 / 5; }
    public int getCourtCost() { return getPrice() * 5 / 4; }
    public int getHouses() { return houses; }
    public boolean hasPool() { return pool; }
    public boolean hasCourt() { return court; }
    /** Returns a copy so callers cannot modify the internal array (no aliasing). */
    public int[] getRents() { return rents.clone(); }
    void setHouses(int houses) { this.houses = houses; }
    void setPool(boolean pool) { this.pool = pool; }
    void setCourt(boolean court) { this.court = court; }

    @Override public String getKind() { return "street"; }

    @Override
    int rent(Game game) {
        if (houses > 0) {
            int extras = (pool ? rents[0] * 25 : 0) + (court ? rents[0] * 25 : 0);
            return rents[houses] + extras;
        }
        return game.ownsFullGroup(getOwner(), group) ? rents[0] * 2 : rents[0];
    }
}
