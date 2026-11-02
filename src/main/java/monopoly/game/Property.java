package monopoly.game;

/** Anything a player can own: streets, stations and utilities. Shares buying, mortgage and the landing logic. */
public abstract class Property extends Square {
    private final int price;
    private Player owner;
    private boolean mortgaged;

    Property(int index, String name, int price) {
        super(index, name);
        this.price = price;
    }

    public int getPrice() { return price; }
    public Player getOwner() { return owner; }
    public boolean isMortgaged() { return mortgaged; }
    public int getMortgageValue() { return price / 2; }
    /** Mortgage value plus 10 % interest, rounded up. */
    public int getUnmortgageCost() { return (int) Math.ceil(getMortgageValue() * 1.1); }
    /** The 10 % the new owner pays the bank when a mortgaged property changes hands. */
    public int getTransferFee() { return (int) Math.ceil(getMortgageValue() * 0.1); }

    /** Rent due right now (each type has its own rule). */
    abstract int rent(Game game);

    void setOwner(Player owner) { this.owner = owner; }
    void setMortgaged(boolean mortgaged) { this.mortgaged = mortgaged; }

    @Override
    void onLand(Game game, Player player) {
        if (owner == null) {
            game.offerPurchase(player, this);
        } else if (owner != player && !mortgaged) {
            game.chargeRent(player, rent(game), owner, getName());
        }
    }
}
