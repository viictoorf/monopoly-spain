package monopoly.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The game engine: all the rules, with no console or web code. Public methods are the player actions; each throws
 * RuleException when the action is not allowed right now (the UI just shows the message).
 *
 * <p>Who acts is given by {@link #getActor()}: normally the current player, but the debtor while a debt is
 * pending, the bidder during an auction and the recipient while a trade offer is waiting for an answer.
 */
public final class Game {
    public enum Phase { ROLL, BUY, AUCTION, MANAGE, DEBT, OVER }

    /** How the rent of the square being landed on is computed (cards can change it). */
    enum RentMode { NORMAL, DOUBLE, TEN }

    /** Rules chosen when the game starts. maxRounds 0 = play until only one player is left. */
    public record Options(int maxRounds, boolean houseRules) {
        public static final Options CLASSIC = new Options(0, false);
    }

    /** Who sits at the table: name, token and whether the computer plays for them. */
    public record PlayerSpec(String name, Avatar avatar, boolean bot) { }

    /** Money somebody owes and cannot pay yet. creditor == null means the bank. */
    public record Debt(Player debtor, Player creditor, int amount, String reason) { }

    public static final int START_CASH = 1500;
    public static final int SALARY = 200;
    public static final int JAIL_FINE = 50;
    public static final int MAX_JAIL_TURNS = 3;
    public static final int MIN_BID = 10;
    static final int BOARD_SIZE = 40;
    static final int JAIL_POSITION = 10;
    private static final int TOTAL_HOUSES = 32;
    private static final int TOTAL_HOTELS = 12;
    private static final int MAX_CAR_ROLLS = 3;
    private static final String[] COLORS = {"#e63946", "#2a9d8f", "#e9a820", "#6a4c93", "#f4742b", "#118ab2"};

    private final Board board = Board.spain();
    private final List<Player> players = new ArrayList<>();
    private final Options options;
    private final Dice dice;
    private final Deck chance;
    private final Deck chest;
    private final List<String> log = new ArrayList<>();

    private int current;
    private int round = 1;
    private Phase phase = Phase.ROLL;
    private Dice.Roll lastRoll;
    private int rollCount;
    private int doubles;            // consecutive doubles this turn
    private boolean extraRoll;      // the player rolls again after resolving this move
    private Property pendingBuy;
    private Auction auction;
    private Trade trade;
    private Debt debt;
    private Runnable afterDebt;     // what to do once the debt is paid (e.g. keep moving, or the next payment)
    private Player winner;
    private int housesLeft = TOTAL_HOUSES;
    private int hotelsLeft = TOTAL_HOTELS;
    private RentMode rentMode = RentMode.NORMAL;

    public Game(List<PlayerSpec> specs, Options options, Dice dice, Random rng) throws RuleException {
        require(specs.size() >= 2 && specs.size() <= 6, "Se necesitan entre 2 y 6 jugadores.");
        require(options.maxRounds() >= 0 && options.maxRounds() <= 500, "Número de rondas no válido.");
        Set<String> names = new HashSet<>();
        Set<Avatar> used = EnumSet.noneOf(Avatar.class);
        for (PlayerSpec spec : specs) {
            if (spec.avatar() != null) {
                require(used.add(spec.avatar()), "Hay fichas repetidas.");
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            PlayerSpec spec = specs.get(i);
            String name = spec.name().strip();
            require(name.matches("[\\p{L}\\p{N} ._-]{1,16}"), "Nombre no válido: usa 1-16 letras o números.");
            require(names.add(name.toLowerCase()), "Hay nombres repetidos.");
            Avatar avatar = spec.avatar();
            for (Avatar a : Avatar.values()) {
                if (avatar == null && used.add(a)) {
                    avatar = a;
                }
            }
            players.add(new Player(name, COLORS[i], avatar, spec.bot(), START_CASH));
        }
        this.options = options;
        this.dice = dice;
        this.chance = new Deck(Cards.chance(), rng);
        this.chest = new Deck(Cards.chest(), rng);
        log("Empieza la partida. Turno de " + current().getName() + ".");
    }

    /** Convenience constructor: classic rules, default tokens, no bots. */
    public Game(List<String> names, Dice dice, Random rng) throws RuleException {
        this(names.stream().map(n -> new PlayerSpec(n, null, false)).toList(), Options.CLASSIC, dice, rng);
    }

    // ---------------------------------------------------------------- read-only state

    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }
    public List<Square> getSquares() { return board.squares(); }
    public Options getOptions() { return options; }
    public Player getCurrent() { return current(); }
    public int getRound() { return round; }
    public Phase getPhase() { return phase; }
    public Dice.Roll getLastRoll() { return lastRoll; }
    public int getRollCount() { return rollCount; }
    public Property getPendingBuy() { return pendingBuy; }
    public Auction getAuction() { return auction; }
    public Trade getTrade() { return trade; }
    public Debt getDebt() { return debt; }
    public Player getWinner() { return winner; }
    public int getHousesLeft() { return housesLeft; }
    public int getHotelsLeft() { return hotelsLeft; }
    public List<String> getLog() { return List.copyOf(log.subList(Math.max(0, log.size() - 40), log.size())); }

    /** The player who has to act now (and whom property management applies to). */
    public Player getActor() {
        if (debt != null) {
            return debt.debtor();
        }
        if (auction != null) {
            return auction.getBidder();
        }
        if (trade != null) {
            return trade.to();
        }
        return current();
    }

    public boolean ownsFullGroup(Player player, ColorGroup group) {
        return groupOf(group).stream().allMatch(s -> s.getOwner() == player);
    }

    /** Cash + properties at full price + buildings: how the winner is chosen when the game ends by time. */
    public int netWorth(Player p) {
        int total = p.getCash();
        for (Property property : p.getProperties()) {
            total += property.isMortgaged() ? property.getMortgageValue() : property.getPrice();
            if (property instanceof Street s) {
                total += s.getHouses() * s.getHouseCost() + (s.hasPool() ? s.getPoolCost() : 0)
                        + (s.hasCourt() ? s.getCourtCost() : 0);
            }
        }
        return total;
    }

    int lastDiceSum() { return lastRoll == null ? 0 : lastRoll.sum(); }
    RentMode rentMode() { return rentMode; }
    void setRentMode(RentMode mode) { rentMode = mode; }

    // ---------------------------------------------------------------- turn actions

    public void roll() throws RuleException {
        turnAction(Phase.ROLL, "Ahora no puedes tirar los dados.");
        Player p = current();
        lastRoll = dice.roll();
        rollCount++;
        extraRoll = false;
        log(p.getName() + " saca " + lastRoll.first() + " y " + lastRoll.second() + ".");
        phase = Phase.MANAGE;   // landing effects may change it to BUY, AUCTION or DEBT
        if (p.isInJail()) {
            rollInJail(p);
        } else if (lastRoll.isDouble() && ++doubles == 3) {
            log("Tres dobles seguidos: ¡a la cárcel! (Dicen que es por conducción temeraria.)");
            sendToJail(p);
        } else {
            if (lastRoll.isDouble()) {
                extraRoll = true;
            } else {
                doubles = 0;
            }
            move(p, lastRoll.sum());
        }
        afterLanding();
    }

    public void buy() throws RuleException {
        turnAction(Phase.BUY, "No hay nada que comprar ahora.");
        Player p = current();
        Property property = pendingBuy;
        require(p.getCash() >= property.getPrice(), "No tienes dinero suficiente.");
        p.spend(property.getPrice());
        assign(property, p);
        pendingBuy = null;
        log(p.getName() + " compra " + property.getName() + " por " + property.getPrice() + " €.");
        phase = Phase.MANAGE;
        afterLanding();
    }

    /** Declining the purchase sends the property to auction: everybody can bid for it. */
    public void declineBuy() throws RuleException {
        turnAction(Phase.BUY, "No hay nada que rechazar.");
        Property property = pendingBuy;
        pendingBuy = null;
        log(current().getName() + " no compra " + property.getName() + ".");
        startAuction(property);
    }

    public void endTurn() throws RuleException {
        turnAction(Phase.MANAGE, "Resuelve tu jugada antes de terminar el turno.");
        nextTurn();
    }

    public void payJailFine() throws RuleException {
        requireJailTurn();
        extraRoll = true;       // after paying, the player still has to roll
        phase = Phase.MANAGE;
        charge(current(), JAIL_FINE, null, "fianza", () -> current().release());
        afterLanding();
    }

    public void useJailCard() throws RuleException {
        requireJailTurn();
        Player p = current();
        require(p.getJailCards() > 0, "No tienes ninguna carta para salir de la cárcel.");
        p.useJailCard().returnJailCard();
        p.release();
        log(p.getName() + " usa su enchufe y sale de la cárcel.");
    }

    /** Ends the game now: the richest player (by net worth) wins. */
    public void finishGame() throws RuleException {
        require(phase == Phase.ROLL || phase == Phase.MANAGE, "Ahora no se puede terminar la partida.");
        require(trade == null, "Hay un trato pendiente.");
        endByWealth();
    }

    // ---------------------------------------------------------------- auctions

    public void bid(int amount) throws RuleException {
        requirePhase(Phase.AUCTION, "No hay ninguna subasta en curso.");
        Player bidder = auction.getBidder();
        require(amount >= MIN_BID, "La puja mínima es de " + MIN_BID + " €.");
        require(amount > auction.getHighest(), "Tienes que superar la puja actual (" + auction.getHighest() + " €).");
        require(amount <= bidder.getCash(), "No tienes tanto dinero.");
        auction.bid(bidder, amount);
        log(bidder.getName() + " puja " + amount + " €.");
        resolveAuctionIfDone();
    }

    public void passAuction() throws RuleException {
        requirePhase(Phase.AUCTION, "No hay ninguna subasta en curso.");
        log(auction.getBidder().getName() + " se retira de la subasta.");
        auction.dropOut();
        resolveAuctionIfDone();
    }

    private void startAuction(Property property) {
        List<Player> order = new ArrayList<>();
        for (int k = 1; k <= players.size(); k++) {
            Player p = players.get((current + k) % players.size());
            if (!p.isBankrupt()) {
                order.add(p);
            }
        }
        auction = new Auction(property, order);
        phase = Phase.AUCTION;
        log("¡Subasta de " + property.getName() + "! Puja mínima: " + MIN_BID + " €.");
    }

    private void resolveAuctionIfDone() {
        if (!auction.finished()) {
            return;
        }
        Property property = auction.getProperty();
        Player buyer = auction.getHighBidder();
        int price = auction.getHighest();
        auction = null;
        if (buyer != null) {
            buyer.spend(price);
            assign(property, buyer);
            buyer.addAuctionWon();
            log(buyer.getName() + " se lleva " + property.getName() + " en la subasta por " + price + " €.");
        } else {
            log("Nadie puja por " + property.getName() + ": sigue libre.");
        }
        phase = Phase.MANAGE;
        afterLanding();
    }

    // ---------------------------------------------------------------- trades

    /** The current player offers a trade to another one. Asked properties belong to "to", given ones to the proposer. */
    public void proposeTrade(int toIndex, int cashGive, int cashAsk, List<Integer> give, List<Integer> ask,
                             int cardsGive, int cardsAsk) throws RuleException {
        require(phase == Phase.ROLL || phase == Phase.MANAGE, "Ahora no puedes proponer un trato.");
        require(trade == null, "Ya hay un trato pendiente.");
        require(toIndex >= 0 && toIndex < players.size(), "Jugador no válido.");
        Trade offer = new Trade(current(), players.get(toIndex), cashGive, cashAsk, give, ask, cardsGive, cardsAsk);
        String why = offer.problem(this);
        require(why == null, why);
        trade = offer;
        log(current().getName() + " propone un trato a " + offer.to().getName() + ": " + offer.describe(this) + ".");
    }

    public void acceptTrade() throws RuleException {
        require(trade != null, "No hay ningún trato pendiente.");
        String why = trade.problem(this);
        require(why == null, why);
        Trade done = trade;
        trade = null;
        done.execute(this);
        log("¡Trato hecho! " + done.describe(this) + ".");
    }

    public void rejectTrade() throws RuleException {
        require(trade != null, "No hay ningún trato pendiente.");
        log(trade.to().getName() + " rechaza el trato de " + trade.from().getName() + ".");
        trade = null;
    }

    public void cancelTrade() throws RuleException {
        require(trade != null, "No hay ningún trato pendiente.");
        log(trade.from().getName() + " retira su oferta.");
        trade = null;
    }

    /** Null if the property can change hands: owned by the player and no buildings in its colour group. */
    String tradableProblem(int index, Player owner) {
        if (index < 0 || index >= BOARD_SIZE || !(board.get(index) instanceof Property property)) {
            return "Casilla no válida en el trato.";
        }
        if (property.getOwner() != owner) {
            return property.getName() + " no es de " + owner.getName() + ".";
        }
        if (property instanceof Street s && groupOf(s.getGroup()).stream().anyMatch(x -> x.getHouses() > 0)) {
            return "No se puede tratar " + s.getName() + ": hay edificios en su grupo.";
        }
        return null;
    }

    // ---------------------------------------------------------------- managing properties

    public String buildProblem(int index) {
        String why = ownedStreetProblem(index);
        if (why != null) {
            return why;
        }
        Street street = (Street) board.get(index);
        Player p = actor();
        List<Street> group = groupOf(street.getGroup());
        if (!group.stream().allMatch(s -> s.getOwner() == p)) {
            return "Necesitas todas las calles del color.";
        }
        if (group.stream().anyMatch(Property::isMortgaged)) {
            return "Hay calles hipotecadas en el grupo.";
        }
        if (street.getHouses() >= 5) {
            return "Ya tiene hotel.";
        }
        if (!group.stream().allMatch(s -> s.getHouses() >= street.getHouses())) {
            return "Debes construir de forma pareja.";
        }
        if (street.getHouses() == 4 ? hotelsLeft <= 0 : housesLeft <= 0) {
            return "No quedan edificios en el banco.";
        }
        return p.getCash() >= street.getHouseCost() ? null : "No tienes dinero suficiente.";
    }

    public void build(int index) throws RuleException {
        String why = buildProblem(index);
        require(why == null, why);
        Street street = (Street) board.get(index);
        Player p = actor();
        p.spend(street.getHouseCost());
        p.addSpentBuilding(street.getHouseCost());
        if (street.getHouses() == 4) {
            hotelsLeft--;
            housesLeft += 4;
        } else {
            housesLeft--;
        }
        street.setHouses(street.getHouses() + 1);
        log(p.getName() + " construye en " + street.getName() + ".");
    }

    public String poolProblem(int index) {
        String why = extraProblem(index);
        if (why != null) {
            return why;
        }
        Street street = (Street) board.get(index);
        if (street.hasPool()) {
            return "Ya tiene piscina.";
        }
        return actor().getCash() >= street.getPoolCost() ? null : "No tienes dinero suficiente.";
    }

    public void buildPool(int index) throws RuleException {
        String why = poolProblem(index);
        require(why == null, why);
        Street street = (Street) board.get(index);
        actor().spend(street.getPoolCost());
        actor().addSpentBuilding(street.getPoolCost());
        street.setPool(true);
        log(actor().getName() + " construye una piscina en " + street.getName() + ".");
    }

    public String courtProblem(int index) {
        String why = extraProblem(index);
        if (why != null) {
            return why;
        }
        Street street = (Street) board.get(index);
        if (!street.hasPool()) {
            return "Antes necesitas una piscina.";
        }
        if (street.hasCourt()) {
            return "Ya tiene pista de deporte.";
        }
        return actor().getCash() >= street.getCourtCost() ? null : "No tienes dinero suficiente.";
    }

    public void buildCourt(int index) throws RuleException {
        String why = courtProblem(index);
        require(why == null, why);
        Street street = (Street) board.get(index);
        actor().spend(street.getCourtCost());
        actor().addSpentBuilding(street.getCourtCost());
        street.setCourt(true);
        log(actor().getName() + " construye una pista de deporte en " + street.getName() + ".");
    }

    /** Pool and court need the house rules, a full unmortgaged group and a hotel on the street. */
    private String extraProblem(int index) {
        if (!options.houseRules()) {
            return "Las piscinas y pistas son una regla de casa: actívala al empezar la partida.";
        }
        String why = ownedStreetProblem(index);
        if (why != null) {
            return why;
        }
        Street street = (Street) board.get(index);
        List<Street> group = groupOf(street.getGroup());
        if (!group.stream().allMatch(s -> s.getOwner() == actor()) || group.stream().anyMatch(Property::isMortgaged)) {
            return "Necesitas todas las calles del color y sin hipotecar.";
        }
        return street.getHouses() == 5 ? null : "Primero necesitas un hotel.";
    }

    public String sellProblem(int index) {
        String why = ownedStreetProblem(index);
        if (why != null) {
            return why;
        }
        Street street = (Street) board.get(index);
        if (street.getHouses() == 0) {
            return "No tiene edificios.";
        }
        if (!groupOf(street.getGroup()).stream().allMatch(s -> s.getHouses() <= street.getHouses())) {
            return "Debes vender de forma pareja.";
        }
        return null;
    }

    /** Sells the most valuable thing on the street: court, then pool, then a house (or the hotel). */
    public void sellBuilding(int index) throws RuleException {
        String why = sellProblem(index);
        require(why == null, why);
        Street street = (Street) board.get(index);
        Player p = actor();
        if (street.hasCourt()) {
            street.setCourt(false);
            p.receive(street.getCourtCost() / 2);
            log(p.getName() + " vende la pista de deporte de " + street.getName() + ".");
            return;
        }
        if (street.hasPool()) {
            street.setPool(false);
            p.receive(street.getPoolCost() / 2);
            log(p.getName() + " vende la piscina de " + street.getName() + ".");
            return;
        }
        if (street.getHouses() == 5) {
            hotelsLeft++;
            if (housesLeft >= 4) {
                housesLeft -= 4;
                street.setHouses(4);
                p.receive(street.getHouseCost() / 2);
            } else {
                // the bank has no houses to replace the hotel: it is torn down completely
                street.setHouses(0);
                p.receive(street.getHouseCost() * 5 / 2);
                log("El banco no tiene casas: se derriba el hotel entero.");
            }
        } else {
            housesLeft++;
            street.setHouses(street.getHouses() - 1);
            p.receive(street.getHouseCost() / 2);
        }
        log(p.getName() + " vende un edificio de " + street.getName() + ".");
    }

    public String mortgageProblem(int index) {
        String why = ownedPropertyProblem(index);
        if (why != null) {
            return why;
        }
        Property property = (Property) board.get(index);
        if (property.isMortgaged()) {
            return "Ya está hipotecada.";
        }
        if (property instanceof Street street && groupOf(street.getGroup()).stream().anyMatch(s -> s.getHouses() > 0)) {
            return "Vende antes los edificios del grupo.";
        }
        return null;
    }

    public void mortgage(int index) throws RuleException {
        String why = mortgageProblem(index);
        require(why == null, why);
        Property property = (Property) board.get(index);
        property.setMortgaged(true);
        actor().receive(property.getMortgageValue());
        log(actor().getName() + " hipoteca " + property.getName() + ".");
    }

    public String unmortgageProblem(int index) {
        String why = ownedPropertyProblem(index);
        if (why != null) {
            return why;
        }
        Property property = (Property) board.get(index);
        if (!property.isMortgaged()) {
            return "No está hipotecada.";
        }
        return actor().getCash() >= property.getUnmortgageCost() ? null : "No tienes dinero suficiente.";
    }

    public void unmortgage(int index) throws RuleException {
        String why = unmortgageProblem(index);
        require(why == null, why);
        Property property = (Property) board.get(index);
        actor().spend(property.getUnmortgageCost());
        property.setMortgaged(false);
        log(actor().getName() + " cancela la hipoteca de " + property.getName() + ".");
    }

    // ---------------------------------------------------------------- debt and bankruptcy

    public void payDebt() throws RuleException {
        requirePhase(Phase.DEBT, "No tienes ninguna deuda pendiente.");
        Debt d = debt;
        require(d.debtor().getCash() >= d.amount(), "Aún no tienes dinero suficiente: vende o hipoteca.");
        Runnable then = afterDebt;
        debt = null;
        afterDebt = null;
        phase = Phase.MANAGE;
        charge(d.debtor(), d.amount(), d.creditor(), d.reason(), then);
        afterLanding();
    }

    public void declareBankruptcy() throws RuleException {
        requirePhase(Phase.DEBT, "Solo puedes declararte en bancarrota si tienes una deuda que no puedes pagar.");
        Player p = debt.debtor();
        Player creditor = debt.creditor();
        Runnable then = afterDebt;
        List<Property> inherited = new ArrayList<>();
        for (Property property : List.copyOf(p.getProperties())) {
            if (property instanceof Street street) {
                if (street.getHouses() == 5) {
                    hotelsLeft++;
                } else {
                    housesLeft += street.getHouses();
                }
                street.setHouses(0);
                street.setPool(false);
                street.setCourt(false);
            }
            p.removeProperty(property);
            if (creditor != null) {
                property.setOwner(creditor);
                creditor.addProperty(property);
                if (property.isMortgaged()) {
                    inherited.add(property);
                }
            } else {
                property.setOwner(null);
                property.setMortgaged(false);
            }
        }
        if (creditor != null) {
            creditor.receive(p.getCash());
            inherited.forEach(property -> payMortgageFee(creditor, property));
        }
        p.takeJailCards().forEach(Deck::returnJailCard);
        p.declareBankrupt();
        boolean wasCurrent = p == current();
        debt = null;
        afterDebt = null;
        pendingBuy = null;
        log(p.getName() + " se declara en bancarrota. ¡A vivir de la paguita!");
        checkWinner();
        if (phase == Phase.OVER) {
            return;
        }
        if (wasCurrent) {
            nextTurn();
        } else {
            phase = Phase.MANAGE;   // somebody else went broke paying the current player: resume the chain
            if (then != null) {
                then.run();
            }
            afterLanding();
        }
    }

    // ---------------------------------------------------------------- engine internals (used by squares and cards)

    /** Moves forward, paying the salary when passing Go, and resolves the destination square. */
    void advance(Player p, int steps) {
        int raw = p.getPosition() + steps;
        goTo(p, Math.floorMod(raw, BOARD_SIZE), raw >= BOARD_SIZE);
    }

    void goTo(Player p, int target, boolean passesGo) {
        if (passesGo) {
            passGo(p);
        }
        p.setPosition(target);
        land(p, target);
    }

    private void passGo(Player p) {
        p.receive(SALARY);
        p.addLap();
        log(p.getName() + " pasa por la Salida y cobra la nómina: " + SALARY + " €.");
    }

    private void land(Player p, int index) {
        Square square = board.get(index);
        log(p.getName() + " cae en " + square.getName() + ".");
        square.onLand(this, p);
    }

    void sendToJail(Player p) {
        p.setPosition(JAIL_POSITION);
        p.jail();
        extraRoll = false;
        log(p.getName() + " va a la cárcel.");
    }

    void offerPurchase(Player p, Property property) {
        if (p.getCash() < property.getPrice()) {
            log(p.getName() + " no puede pagar " + property.getName() + " (" + property.getPrice() + " €): a subasta.");
            startAuction(property);
            return;
        }
        pendingBuy = property;
        phase = Phase.BUY;
    }

    void chargeRent(Player payer, int amount, Player owner, String property) {
        charge(payer, amount, owner, "alquiler de " + property, () -> {
            payer.addRentPaid(amount);
            owner.addRentReceived(amount);
        });
    }

    void charge(Player payer, int amount, Player creditor, String reason) {
        charge(payer, amount, creditor, reason, null);
    }

    /** Pays now if possible; otherwise the game enters the DEBT phase and `then` runs once it is paid. */
    void charge(Player payer, int amount, Player creditor, String reason, Runnable then) {
        if (payer.getCash() >= amount) {
            payer.spend(amount);
            if (creditor != null) {
                creditor.receive(amount);
            }
            log(payer.getName() + " paga " + amount + " € (" + reason + ")"
                    + (creditor != null ? " a " + creditor.getName() : "") + ".");
            if (then != null) {
                then.run();
            }
            return;
        }
        debt = new Debt(payer, creditor, amount, reason);
        afterDebt = then;
        phase = Phase.DEBT;
        log(payer.getName() + " debe " + amount + " € (" + reason + ") y no tiene dinero: vende, hipoteca o se arruina.");
    }

    void drawCard(Player p, boolean isChance) {
        Deck deck = isChance ? chance : chest;
        Card card = deck.draw();
        log((isChance ? "Suerte: " : "Junta de Vecinos: ") + card.text());
        CardEffects.apply(this, p, card, deck);
    }

    /** A card that makes the player roll again (nearest utility). */
    void rollForCard() {
        lastRoll = dice.roll();
        rollCount++;
        log("Tira los dados para la factura: " + lastRoll.first() + " y " + lastRoll.second() + ".");
    }

    void transfer(Property property, Player from, Player to) {
        from.removeProperty(property);
        to.addProperty(property);
        property.setOwner(to);
        if (property.isMortgaged()) {
            payMortgageFee(to, property);
        }
    }

    private void payMortgageFee(Player payer, Property property) {
        int fee = Math.min(payer.getCash(), property.getTransferFee());
        payer.spend(fee);
        log(payer.getName() + " paga " + fee + " € al banco (10 % de " + property.getName() + ", hipotecada).");
    }

    private void assign(Property property, Player owner) {
        property.setOwner(owner);
        owner.addProperty(property);
    }

    void log(String message) { log.add(message); }

    List<Player> activeOthers(Player p) {
        return players.stream().filter(o -> o != p && !o.isBankrupt()).toList();
    }

    // ---------------------------------------------------------------- movement variants (house rules)

    private void move(Player p, int steps) {
        if (options.houseRules() && p.getAvatar() == Avatar.PELOTA) {
            ballMove(p, steps);
        } else if (options.houseRules() && p.getAvatar() == Avatar.COCHE) {
            carMove(p, steps);
        } else {
            advance(p, steps);
        }
    }

    /** Ball: 4 or less goes backwards; more advances 4 squares and then only stops on odd steps (and the last). */
    private void ballMove(Player p, int steps) {
        if (steps <= 4) {
            log("El balón rebota hacia atrás " + steps + " casillas.");
            goTo(p, Math.floorMod(p.getPosition() - steps, BOARD_SIZE), false);
            return;
        }
        log("El balón avanza a botes: solo se detiene en las casillas impares a partir de la cuarta.");
        for (int i = 1; i <= steps; i++) {
            int next = (p.getPosition() + 1) % BOARD_SIZE;
            if (next == 0) {
                passGo(p);
            }
            p.setPosition(next);
            if (i == steps || (i > 4 && i % 2 == 1)) {
                land(p, next);
                if (phase != Phase.MANAGE || p.isInJail() || p.isBankrupt() || p.getPosition() != next) {
                    return;
                }
            }
        }
    }

    /** Car: more than 4 advances and may roll again (up to 3 extra times); 4 or less reverses and loses 2 turns. */
    private void carMove(Player p, int steps) {
        if (steps > 4) {
            advance(p, steps);
            if (p.getCarRolls() < MAX_CAR_ROLLS && !p.isInJail() && !p.isBankrupt()) {
                p.addCarRoll();
                extraRoll = true;
                log("El coche aprovecha para volver a tirar.");
            }
        } else {
            log("Marcha atrás: el coche retrocede " + steps + " casillas y se queda 2 turnos en el taller.");
            p.setSkipTurns(2);
            goTo(p, Math.floorMod(p.getPosition() - steps, BOARD_SIZE), false);
        }
    }

    // ---------------------------------------------------------------- private helpers

    private Player current() { return players.get(current); }

    private Player actor() { return getActor(); }

    private void rollInJail(Player p) {
        if (lastRoll.isDouble()) {
            p.release();
            log("Dobles: " + p.getName() + " sale de la cárcel.");
            move(p, lastRoll.sum());
            return;
        }
        p.addJailTurn();
        if (p.getJailTurns() >= MAX_JAIL_TURNS) {
            log("Tercer intento fallido: paga la fianza y avanza.");
            int steps = lastRoll.sum();
            charge(p, JAIL_FINE, null, "fianza", () -> {
                p.release();
                move(p, steps);
            });
        } else {
            log(p.getName() + " no saca dobles y sigue en la cárcel.");
        }
    }

    /** After a move resolves: if nothing is pending, either roll again (doubles) or manage/end the turn. */
    private void afterLanding() {
        if (phase == Phase.MANAGE) {
            Player p = current();
            phase = extraRoll && !p.isInJail() && !p.isBankrupt() ? Phase.ROLL : Phase.MANAGE;
        }
    }

    private void nextTurn() {
        current().resetCarRolls();
        doubles = 0;
        extraRoll = false;
        while (true) {
            int previous = current;
            current = (current + 1) % players.size();
            if (current <= previous) {
                round++;
                log("— Ronda " + round + " —");
            }
            Player next = current();
            if (next.isBankrupt()) {
                continue;
            }
            if (next.getSkipTurns() > 0) {
                next.setSkipTurns(next.getSkipTurns() - 1);
                log(next.getName() + " pierde el turno: el coche sigue en el taller.");
                continue;
            }
            break;
        }
        if (options.maxRounds() > 0 && round > options.maxRounds()) {
            endByWealth();
            return;
        }
        phase = Phase.ROLL;
        log("Turno de " + current().getName() + ".");
    }

    private void checkWinner() {
        List<Player> alive = players.stream().filter(p -> !p.isBankrupt()).toList();
        if (alive.size() == 1) {
            winner = alive.get(0);
            phase = Phase.OVER;
            log("¡" + winner.getName() + " gana la partida!");
        }
    }

    private void endByWealth() {
        winner = players.stream().filter(p -> !p.isBankrupt())
                .max(Comparator.comparingInt(this::netWorth).thenComparingInt(Player::getCash)).orElseThrow();
        phase = Phase.OVER;
        log("Fin de la partida. ¡" + winner.getName() + " gana con un patrimonio de " + netWorth(winner) + " €!");
    }

    private List<Street> groupOf(ColorGroup group) {
        return board.squares().stream().filter(Street.class::isInstance).map(Street.class::cast)
                .filter(s -> s.getGroup() == group).toList();
    }

    private String ownedPropertyProblem(int index) {
        if (phase == Phase.OVER) {
            return "La partida ha terminado.";
        }
        if (index < 0 || index >= BOARD_SIZE || !(board.get(index) instanceof Property property)) {
            return "Casilla no válida.";
        }
        return property.getOwner() == actor() ? null : "Esa propiedad no es tuya.";
    }

    private String ownedStreetProblem(int index) {
        String why = ownedPropertyProblem(index);
        if (why != null) {
            return why;
        }
        return board.get(index) instanceof Street ? null : "Solo se puede construir en calles.";
    }

    private void requireJailTurn() throws RuleException {
        require(trade == null, "Hay un trato pendiente: respóndelo o cancélalo antes de seguir.");
        require(phase == Phase.ROLL && current().isInJail(), "Solo puedes hacerlo al inicio de tu turno en la cárcel.");
    }

    /** A normal turn action: right phase and no trade waiting for an answer. */
    private void turnAction(Phase expected, String message) throws RuleException {
        requirePhase(expected, message);
        require(trade == null, "Hay un trato pendiente: respóndelo o cancélalo antes de seguir.");
    }

    private void requirePhase(Phase expected, String message) throws RuleException {
        require(phase == expected, message);
    }

    private static void require(boolean condition, String message) throws RuleException {
        if (!condition) {
            throw new RuleException(message);
        }
    }
}
