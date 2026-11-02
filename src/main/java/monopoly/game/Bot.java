package monopoly.game;

import java.util.List;

/**
 * A simple computer player. It only uses the engine's public API and answers with the same text actions a human
 * produces, so its moves are recorded and replayed like any other. The strategy is deliberately plain: buy while there
 * is a cash reserve, bid up to what a property is worth, build evenly, and trade only to complete a colour group.
 */
public final class Bot {
    private static final int RESERVE = 150;

    private Bot() { }

    /** The action the current actor (a bot) wants to play, or null if the game is over. */
    public static String decide(Game g) {
        Player me = g.getActor();
        if (g.getTrade() != null) {
            return answerTrade(g, me, g.getTrade()) ? "accept" : "reject";
        }
        return switch (g.getPhase()) {
            case ROLL -> roll(me);
            case BUY -> buy(g, me);
            case AUCTION -> bid(g, me);
            case MANAGE -> manage(g, me);
            case DEBT -> debt(g, me);
            case OVER -> null;
        };
    }

    /** A move that is always legal, used if the preferred one is rejected (so a bot can never get stuck). */
    public static String fallback(Game g) {
        if (g.getTrade() != null) {
            return "reject";
        }
        return switch (g.getPhase()) {
            case ROLL -> "roll";
            case BUY -> "decline";
            case AUCTION -> "pass";
            case MANAGE -> "end";
            case DEBT -> "bankrupt";
            case OVER -> null;
        };
    }

    private static String roll(Player me) {
        if (me.isInJail()) {
            if (me.getJailCards() > 0) {
                return "jailcard";
            }
            if (me.getCash() > 400) {
                return "jailfine";
            }
        }
        return "roll";
    }

    private static String buy(Game g, Player me) {
        Property property = g.getPendingBuy();
        boolean affordable = me.getCash() - property.getPrice() >= RESERVE;
        return affordable || completesGroup(g, me, property) ? "buy" : "decline";
    }

    private static String bid(Game g, Player me) {
        Auction auction = g.getAuction();
        Property property = auction.getProperty();
        int max = (int) Math.min(me.getCash() - 30, worth(g, me, property) * 1.05);
        int high = auction.getHighest();
        if (max < Math.max(Game.MIN_BID, high + 1)) {
            return "pass";
        }
        int next = high == 0
                ? Math.max(Game.MIN_BID, Math.min(max, property.getPrice() / 3))
                : Math.min(max, high + Math.max(10, (max - high) / 3));
        return "bid:" + next;
    }

    private static String manage(Game g, Player me) {
        for (Property p : me.getProperties()) {
            if (p.isMortgaged() && me.getCash() > p.getUnmortgageCost() + 500 && g.unmortgageProblem(p.getIndex()) == null) {
                return "unmortgage:" + p.getIndex();
            }
        }
        String offer = proposeTrade(g, me);
        if (offer != null) {
            return offer;
        }
        Street best = null;
        for (Property p : me.getProperties()) {
            if (p instanceof Street s && g.buildProblem(s.getIndex()) == null && me.getCash() - s.getHouseCost() >= RESERVE
                    && (best == null || s.getHouses() < best.getHouses()
                    || (s.getHouses() == best.getHouses() && s.getPrice() > best.getPrice()))) {
                best = s;
            }
        }
        if (best != null) {
            return "build:" + best.getIndex();
        }
        for (Property p : me.getProperties()) {
            if (p instanceof Street s && g.getOptions().houseRules()) {
                if (g.poolProblem(s.getIndex()) == null && me.getCash() - s.getPoolCost() >= 400) {
                    return "pool:" + s.getIndex();
                }
                if (g.courtProblem(s.getIndex()) == null && me.getCash() - s.getCourtCost() >= 400) {
                    return "court:" + s.getIndex();
                }
            }
        }
        return "end";
    }

    private static String debt(Game g, Player me) {
        if (me.getCash() >= g.getDebt().amount()) {
            return "paydebt";
        }
        Street sell = null;
        for (Property p : me.getProperties()) {
            if (p instanceof Street s && g.sellProblem(s.getIndex()) == null
                    && (sell == null || s.getHouses() > sell.getHouses())) {
                sell = s;
            }
        }
        if (sell != null) {
            return "sell:" + sell.getIndex();
        }
        Property cheapest = null;
        for (Property p : me.getProperties()) {
            if (g.mortgageProblem(p.getIndex()) == null && (cheapest == null || p.getPrice() < cheapest.getPrice())) {
                cheapest = p;
            }
        }
        return cheapest != null ? "mortgage:" + cheapest.getIndex() : "bankrupt";
    }

    // ---------------------------------------------------------------- trading

    /** Offers cash for the one street that would complete one of its colour groups (once per round). */
    private static String proposeTrade(Game g, Player me) {
        if (me.getLastTradeRound() == g.getRound()) {
            return null;
        }
        for (Square sq : g.getSquares()) {
            if (sq instanceof Street s && s.getOwner() != null && s.getOwner() != me && !s.getOwner().isBankrupt()
                    && !s.isMortgaged() && completesGroup(g, me, s) && groupHasNoBuildings(g, s)) {
                int offer = (int) Math.min(me.getCash() - 100, s.getPrice() * 2.2);
                if (offer >= s.getPrice() * 1.4) {
                    me.setLastTradeRound(g.getRound());
                    return "trade:" + g.getPlayers().indexOf(s.getOwner()) + ":" + offer + ":0::" + s.getIndex() + ":0:0";
                }
            }
        }
        return null;
    }

    private static boolean answerTrade(Game g, Player me, Trade t) {
        boolean imTo = me == t.to();
        Player other = imTo ? t.from() : t.to();
        int cashIn = imTo ? t.cashGive() : t.cashAsk();
        int cashOut = imTo ? t.cashAsk() : t.cashGive();
        List<Integer> in = imTo ? t.give() : t.ask();
        List<Integer> out = imTo ? t.ask() : t.give();
        double received = cashIn;
        double given = cashOut;
        for (int i : in) {
            received += worth(g, me, (Property) g.getSquares().get(i));
        }
        for (int i : out) {
            Property p = (Property) g.getSquares().get(i);
            given += p.getPrice() * (completesGroup(g, other, p) ? 2.5 : 1.0);
        }
        return received >= given * 1.15;
    }

    // ---------------------------------------------------------------- valuation

    private static List<Street> group(Game g, ColorGroup color) {
        return g.getSquares().stream().filter(Street.class::isInstance).map(Street.class::cast)
                .filter(s -> s.getGroup() == color).toList();
    }

    private static boolean completesGroup(Game g, Player who, Property p) {
        return p instanceof Street s && group(g, s.getGroup()).stream().allMatch(x -> x == s || x.getOwner() == who);
    }

    private static boolean groupHasNoBuildings(Game g, Street s) {
        return group(g, s.getGroup()).stream().allMatch(x -> x.getHouses() == 0);
    }

    /** What a property is worth to this player: price, more if it completes a set or builds on one he is collecting. */
    private static double worth(Game g, Player me, Property p) {
        double value = p.getPrice();
        if (completesGroup(g, me, p)) {
            value *= 2.2;
        } else if (p instanceof Station) {
            value *= 1 + 0.25 * me.count(Station.class);
        } else if (p instanceof Street s) {
            long mine = group(g, s.getGroup()).stream().filter(x -> x.getOwner() == me).count();
            value *= 1 + 0.2 * mine;
        }
        return p.isMortgaged() ? value * 0.7 : value;
    }
}
