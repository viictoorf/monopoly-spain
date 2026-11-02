package monopoly.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** A participant. Only the engine (same package) can change money or position: the mutators are package-private. */
public final class Player {
    private final String name;
    private final String color;
    private final Avatar avatar;
    private final boolean bot;
    private int cash;
    private int position;
    private boolean inJail;
    private int jailTurns;
    private boolean bankrupt;
    private int skipTurns;          // house rules: turns the car loses after reversing
    private int carRolls;           // house rules: extra rolls the car took this turn
    private int lastTradeRound;     // used by the bot so it does not spam offers
    private final List<Property> properties = new ArrayList<>();
    private final Deque<Deck> jailCards = new ArrayDeque<>();

    // statistics shown at the end of the game
    private int rentPaid;
    private int rentReceived;
    private int taxPaid;
    private int spentBuilding;
    private int laps;
    private int jailVisits;
    private int auctionsWon;

    Player(String name, String color, Avatar avatar, boolean bot, int cash) {
        this.name = name;
        this.color = color;
        this.avatar = avatar;
        this.bot = bot;
        this.cash = cash;
    }

    public String getName() { return name; }
    public String getColor() { return color; }
    public Avatar getAvatar() { return avatar; }
    public boolean isBot() { return bot; }
    public int getCash() { return cash; }
    public int getPosition() { return position; }
    public boolean isInJail() { return inJail; }
    public int getJailTurns() { return jailTurns; }
    public int getJailCards() { return jailCards.size(); }
    public boolean isBankrupt() { return bankrupt; }
    public int getSkipTurns() { return skipTurns; }
    public int getCarRolls() { return carRolls; }
    public int getLastTradeRound() { return lastTradeRound; }
    public int getRentPaid() { return rentPaid; }
    public int getRentReceived() { return rentReceived; }
    public int getTaxPaid() { return taxPaid; }
    public int getSpentBuilding() { return spentBuilding; }
    public int getLaps() { return laps; }
    public int getJailVisits() { return jailVisits; }
    public int getAuctionsWon() { return auctionsWon; }

    /** Read-only view: callers cannot alter the player's list behind its back (avoids aliasing). */
    public List<Property> getProperties() { return Collections.unmodifiableList(properties); }

    public int count(Class<? extends Property> type) {
        return (int) properties.stream().filter(type::isInstance).count();
    }

    void receive(int amount) { cash += amount; }

    void spend(int amount) {
        if (amount > cash) {
            throw new IllegalStateException("The engine must check funds before spending");
        }
        cash -= amount;
    }

    void setPosition(int position) { this.position = position; }
    void jail() { inJail = true; jailTurns = 0; jailVisits++; }
    void release() { inJail = false; jailTurns = 0; }
    void addJailTurn() { jailTurns++; }
    void addJailCard(Deck origin) { jailCards.push(origin); }
    Deck useJailCard() { return jailCards.pop(); }

    List<Deck> takeJailCards() {
        List<Deck> all = new ArrayList<>(jailCards);
        jailCards.clear();
        return all;
    }

    void addProperty(Property property) { properties.add(property); }
    void removeProperty(Property property) { properties.remove(property); }
    void declareBankrupt() { bankrupt = true; cash = 0; }

    void setSkipTurns(int turns) { skipTurns = turns; }
    void addCarRoll() { carRolls++; }
    void resetCarRolls() { carRolls = 0; }
    void setLastTradeRound(int round) { lastTradeRound = round; }
    void addRentPaid(int amount) { rentPaid += amount; }
    void addRentReceived(int amount) { rentReceived += amount; }
    void addTaxPaid(int amount) { taxPaid += amount; }
    void addSpentBuilding(int amount) { spentBuilding += amount; }
    void addLap() { laps++; }
    void addAuctionWon() { auctionsWon++; }
}
