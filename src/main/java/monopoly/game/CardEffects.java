package monopoly.game;

import java.util.List;

/**
 * What each card does. Kept out of Game so the engine stays readable. Payments between players are chained one by
 * one (each payment is the continuation of the previous one), so a player who cannot pay simply enters the debt
 * phase and the rest of the chain resumes afterwards.
 */
final class CardEffects {
    private CardEffects() { }

    static void apply(Game g, Player p, Card card, Deck deck) {
        switch (card.effect()) {
            case COLLECT -> p.receive(card.a());
            case PAY -> g.charge(p, card.a(), null, "carta");
            case MOVE_TO -> g.goTo(p, card.a(), card.a() < p.getPosition());
            case MOVE_BACK -> g.goTo(p, Math.floorMod(p.getPosition() - card.a(), Game.BOARD_SIZE), false);
            case GO_TO_JAIL -> g.sendToJail(p);
            case JAIL_FREE -> p.addJailCard(deck);
            case COLLECT_FROM_ALL -> collect(g, p, g.activeOthers(p), 0, card.a());
            case PAY_TO_ALL -> pay(g, p, g.activeOthers(p), 0, card.a());
            case REPAIRS -> {
                int cost = 0;
                for (Property property : p.getProperties()) {
                    if (property instanceof Street s) {
                        cost += s.getHouses() == 5 ? card.b() : s.getHouses() * card.a();
                    }
                }
                if (cost > 0) {
                    g.charge(p, cost, null, "derrama");
                }
            }
            case NEAREST_STATION -> nearest(g, p, Station.class, Game.RentMode.DOUBLE);
            case NEAREST_UTILITY -> nearest(g, p, Utility.class, Game.RentMode.TEN);
        }
    }

    private static void collect(Game g, Player p, List<Player> others, int i, int amount) {
        if (i >= others.size()) {
            return;
        }
        Player other = others.get(i);
        if (other.isBankrupt()) {
            collect(g, p, others, i + 1, amount);
        } else {
            g.charge(other, amount, p, "cumpleaños de " + p.getName(), () -> collect(g, p, others, i + 1, amount));
        }
    }

    private static void pay(Game g, Player p, List<Player> others, int i, int amount) {
        if (i >= others.size()) {
            return;
        }
        Player other = others.get(i);
        if (other.isBankrupt()) {
            pay(g, p, others, i + 1, amount);
        } else {
            g.charge(p, amount, other, "ronda de cañas", () -> pay(g, p, others, i + 1, amount));
        }
    }

    private static void nearest(Game g, Player p, Class<? extends Property> type, Game.RentMode mode) {
        int target = p.getPosition();
        do {
            target = (target + 1) % Game.BOARD_SIZE;
        } while (!type.isInstance(g.getSquares().get(target)));
        Property destination = (Property) g.getSquares().get(target);
        if (mode == Game.RentMode.TEN && destination.getOwner() != null && destination.getOwner() != p) {
            g.rollForCard();
        }
        g.setRentMode(mode);
        try {
            g.goTo(p, target, target < p.getPosition());
        } finally {
            g.setRentMode(Game.RentMode.NORMAL);
        }
    }
}
