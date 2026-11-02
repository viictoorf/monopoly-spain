package monopoly.game;

import java.util.ArrayList;
import java.util.List;

/**
 * An auction in progress. Bidders take turns, in order: each one either raises the bid or drops out.
 * It ends when everybody but the highest bidder has dropped out (or when nobody bids at all).
 */
public final class Auction {
    private final Property property;
    private final List<Player> bidders;
    private int index;
    private int highest;
    private Player highBidder;

    Auction(Property property, List<Player> order) {
        this.property = property;
        this.bidders = new ArrayList<>(order);
    }

    public Property getProperty() { return property; }
    /** Whose turn it is to bid or drop out. */
    public Player getBidder() { return bidders.get(index); }
    public int getHighest() { return highest; }
    public Player getHighBidder() { return highBidder; }
    public List<Player> getBidders() { return List.copyOf(bidders); }

    void bid(Player player, int amount) {
        highest = amount;
        highBidder = player;
        index = (index + 1) % bidders.size();
    }

    void dropOut() {
        bidders.remove(index);
        if (!bidders.isEmpty()) {
            index %= bidders.size();
        }
    }

    boolean finished() {
        return bidders.isEmpty() || (highBidder != null && bidders.size() == 1);
    }
}
