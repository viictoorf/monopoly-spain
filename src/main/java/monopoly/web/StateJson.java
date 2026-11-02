package monopoly.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import monopoly.game.Auction;
import monopoly.game.Game;
import monopoly.game.Player;
import monopoly.game.Property;
import monopoly.game.Session;
import monopoly.game.Square;
import monopoly.game.Squares;
import monopoly.game.Street;
import monopoly.game.Trade;

/** Serialises the game state to JSON by hand, so the project needs no external libraries. */
final class StateJson {
    private StateJson() { }

    /** Builds an object from alternating key, value arguments (keeps the order). */
    static Map<String, Object> o(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /** Writes any of: null, Boolean, Number, String, Map, Collection, int[]. */
    static String json(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return q(s);
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder("{");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(b.length() > 1 ? "," : "").append(q((String) e.getKey())).append(':').append(json(e.getValue()));
            }
            return b.append('}').toString();
        }
        if (v instanceof Collection<?> c) {
            StringBuilder b = new StringBuilder("[");
            for (Object x : c) {
                b.append(b.length() > 1 ? "," : "").append(json(x));
            }
            return b.append(']').toString();
        }
        if (v instanceof int[] a) {
            StringBuilder b = new StringBuilder("[");
            for (int x : a) {
                b.append(b.length() > 1 ? "," : "").append(x);
            }
            return b.append(']').toString();
        }
        return v.toString();
    }

    /** The state for the page when no game is running: whether there is a saved game to continue. */
    static String idle(Session saved, int rev) {
        Object info = saved == null ? Boolean.FALSE
                : o("players", saved.setup().players().stream().map(Game.PlayerSpec::name).toList(),
                    "round", saved.game().getRound(), "over", saved.game().getPhase() == Game.Phase.OVER);
        return json(o("started", false, "rev", rev, "saved", info));
    }

    static String of(Session session, int rev, boolean local) {
        Game g = session.game();
        List<Player> ps = g.getPlayers();
        Game.Options opts = g.getOptions();
        Map<String, Object> m = o(
                "started", true, "rev", rev, "host", local, "canUndo", session.size() > 0,
                "current", ps.indexOf(g.getCurrent()), "actor", ps.indexOf(g.getActor()),
                "phase", g.getPhase().name(), "round", g.getRound(), "maxRounds", opts.maxRounds(),
                "house", opts.houseRules(), "rolls", g.getRollCount(),
                "dice", g.getLastRoll() == null ? null : List.of(g.getLastRoll().first(), g.getLastRoll().second()),
                "pending", g.getPendingBuy() == null ? -1 : g.getPendingBuy().getIndex(),
                "housesLeft", g.getHousesLeft(), "hotelsLeft", g.getHotelsLeft(),
                "winner", g.getWinner() == null ? -1 : ps.indexOf(g.getWinner()));
        Game.Debt d = g.getDebt();
        m.put("debt", d == null ? null : o("debtor", ps.indexOf(d.debtor()), "creditor", d.creditor() == null ? -1 : ps.indexOf(d.creditor()),
                "amount", d.amount(), "reason", d.reason()));
        Auction a = g.getAuction();
        m.put("auction", a == null ? null : o("square", a.getProperty().getIndex(), "bidder", ps.indexOf(a.getBidder()),
                "highest", a.getHighest(), "highBidder", a.getHighBidder() == null ? -1 : ps.indexOf(a.getHighBidder()),
                "bidders", a.getBidders().stream().map(ps::indexOf).toList()));
        Trade t = g.getTrade();
        m.put("trade", t == null ? null : o("from", ps.indexOf(t.from()), "to", ps.indexOf(t.to()), "cashGive", t.cashGive(),
                "cashAsk", t.cashAsk(), "give", t.give(), "ask", t.ask(), "cardsGive", t.cardsGive(), "cardsAsk", t.cardsAsk()));
        List<Object> players = new ArrayList<>();
        for (Player p : ps) {
            players.add(o("name", p.getName(), "color", p.getColor(), "avatar", p.getAvatar().name(), "bot", p.isBot(),
                    "cash", p.getCash(), "pos", p.getPosition(), "jail", p.isInJail(), "jailCards", p.getJailCards(),
                    "bankrupt", p.isBankrupt(), "skip", p.getSkipTurns(), "worth", g.netWorth(p),
                    "props", p.getProperties().stream().map(Property::getIndex).toList(),
                    "stats", o("rentPaid", p.getRentPaid(), "rentReceived", p.getRentReceived(), "taxPaid", p.getTaxPaid(),
                            "built", p.getSpentBuilding(), "laps", p.getLaps(), "jails", p.getJailVisits(), "auctions", p.getAuctionsWon())));
        }
        m.put("players", players);
        List<Object> squares = new ArrayList<>();
        for (Square s : g.getSquares()) {
            Map<String, Object> sq = o("i", s.getIndex(), "name", s.getName(), "short", s.getShortName(), "blurb", s.getBlurb(), "kind", s.getKind());
            if (s instanceof Squares.Tax tax) {
                sq.put("amount", tax.getAmount());
            }
            if (s instanceof Property p) {
                sq.put("price", p.getPrice());
                sq.put("mortgage", p.getMortgageValue());
                sq.put("unmortgage", p.getUnmortgageCost());
                sq.put("owner", p.getOwner() == null ? -1 : ps.indexOf(p.getOwner()));
                sq.put("mortgaged", p.isMortgaged());
            }
            if (s instanceof Street st) {
                sq.put("color", st.getGroup().hex());
                sq.put("group", st.getGroup().name());
                sq.put("houses", st.getHouses());
                sq.put("pool", st.hasPool());
                sq.put("court", st.hasCourt());
                sq.put("houseCost", st.getHouseCost());
                sq.put("poolCost", st.getPoolCost());
                sq.put("courtCost", st.getCourtCost());
                sq.put("rents", st.getRents());
            }
            squares.add(sq);
        }
        m.put("squares", squares);
        m.put("log", g.getLog());
        return json(m);
    }
}
