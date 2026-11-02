package monopoly.game;

import java.util.List;
import java.util.Random;
import monopoly.game.Game.Options;
import monopoly.game.Game.PlayerSpec;

/** Tests for auctions, trades, chained debts, house rules, and save/undo (run with: ./test.sh). */
public final class RulesTest {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private interface Action { void run() throws RuleException; }

    private static void expectRule(Action a, String what) {
        try {
            a.run();
            check(false, what + " (no exception)");
        } catch (RuleException e) {
            // expected
        }
    }

    private static Game game(Options options, List<PlayerSpec> specs, int... dice) throws RuleException {
        int[] i = {0};
        return new Game(specs, options, new Dice(() -> dice[i[0]++]), new Random(1));
    }

    private static List<PlayerSpec> two() {
        return List.of(new PlayerSpec("Ana", null, false), new PlayerSpec("Luis", null, false));
    }

    private static List<PlayerSpec> three() {
        return List.of(new PlayerSpec("Ana", null, false), new PlayerSpec("Luis", null, false), new PlayerSpec("Marta", null, false));
    }

    private static void give(Game g, int square, int player) {
        Property p = (Property) g.getSquares().get(square);
        p.setOwner(g.getPlayers().get(player));
        g.getPlayers().get(player).addProperty(p);
    }

    public static void main(String[] args) throws Exception {
        boardTest();
        auctionTest();
        tradeTest();
        debtChainTest();
        houseRulesTest();
        sessionTest();
        System.out.println(failures == 0 ? "RulesTest: all passed" : failures + " test(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void boardTest() throws Exception {
        Game g = game(Options.CLASSIC, two());
        String[] names = {"Madrid", "Barcelona", "Estación de Atocha", "Pamplona", "Valencia"};
        int[] idx = {39, 37, 5, 24, 32};
        for (int k = 0; k < names.length; k++) {
            check(g.getSquares().get(idx[k]).getName().equals(names[k]), "card target " + names[k] + " at " + idx[k]);
        }
        check(g.getSquares().size() == 40 && g.getSquares().stream().filter(Street.class::isInstance).count() == 22, "board shape");
    }

    private static void auctionTest() throws Exception {
        // Declining sends the street to auction; Luis bids first, Ana outbids, Luis drops: Ana pays her bid.
        Game g = game(Options.CLASSIC, two(), 1, 2);
        g.roll();
        g.declineBuy();
        check(g.getPhase() == Game.Phase.AUCTION && g.getActor().getName().equals("Luis"), "auction starts with the next player");
        expectRule(() -> g.bid(5), "bid below the minimum");
        expectRule(() -> g.bid(5000), "bid above the cash");
        g.bid(30);
        expectRule(() -> g.bid(30), "bid not above the current one");
        g.bid(40);
        g.passAuction();
        Player ana = g.getPlayers().get(0);
        check(g.getPhase() == Game.Phase.MANAGE && ana.getCash() == 1460 && g.getSquares().get(3) instanceof Property p && p.getOwner() == ana,
                "highest bidder wins and pays the bid");

        // Nobody bids: the property stays free.
        Game h = game(Options.CLASSIC, two(), 1, 2);
        h.roll();
        h.declineBuy();
        h.passAuction();
        h.passAuction();
        check(h.getPhase() == Game.Phase.MANAGE && ((Property) h.getSquares().get(3)).getOwner() == null, "no bids: still free");

        // Landing on something you cannot afford goes straight to auction.
        Game k = game(Options.CLASSIC, two(), 1, 2);
        k.getPlayers().get(0).spend(1450);
        k.roll();
        check(k.getPhase() == Game.Phase.AUCTION, "cannot afford: auction");
    }

    private static void tradeTest() throws Exception {
        Game g = game(Options.CLASSIC, two());
        give(g, 1, 0);
        Player ana = g.getPlayers().get(0);
        Player luis = g.getPlayers().get(1);
        expectRule(() -> g.proposeTrade(1, 0, 0, List.of(), List.of(), 0, 0), "empty trade");
        expectRule(() -> g.proposeTrade(1, 5000, 0, List.of(), List.of(), 0, 0), "more cash than owned");
        expectRule(() -> g.proposeTrade(1, 0, 0, List.of(3), List.of(), 0, 0), "give a property you do not own");
        g.proposeTrade(1, 0, 100, List.of(1), List.of(), 0, 0);
        check(g.getActor() == luis, "the recipient answers");
        expectRule(g::roll, "cannot roll with a pending trade");
        g.acceptTrade();
        check(((Property) g.getSquares().get(1)).getOwner() == luis && ana.getCash() == 1600 && luis.getCash() == 1400, "trade executed");

        // A mortgaged property costs its new owner the 10 % fee.
        Game m = game(Options.CLASSIC, two());
        give(m, 1, 0);
        m.mortgage(1);
        m.proposeTrade(1, 0, 0, List.of(1), List.of(), 0, 0);
        m.acceptTrade();
        check(m.getPlayers().get(1).getCash() == 1497, "mortgage transfer fee (3 €)");

        // Buildings in the group block trading.
        Game b = game(Options.CLASSIC, two());
        give(b, 1, 0);
        give(b, 3, 0);
        b.build(1);
        expectRule(() -> b.proposeTrade(1, 0, 0, List.of(1), List.of(), 0, 0), "trade a street with buildings in its group");

        // Reject and cancel.
        Game r = game(Options.CLASSIC, two());
        r.proposeTrade(1, 50, 0, List.of(), List.of(), 0, 0);
        r.rejectTrade();
        check(r.getTrade() == null && r.getPlayers().get(1).getCash() == 1500, "rejected trade changes nothing");
        r.proposeTrade(1, 50, 0, List.of(), List.of(), 0, 0);
        r.cancelTrade();
        check(r.getTrade() == null, "cancelled trade");
    }

    private static void debtChainTest() throws Exception {
        Card birthday = new Card("x", Card.Effect.COLLECT_FROM_ALL, 10, 0);

        // A broke player owes the birthday money; after paying, the chain resumes with the next one.
        Game g = game(Options.CLASSIC, three());
        Player ana = g.getPlayers().get(0);
        Player luis = g.getPlayers().get(1);
        Player marta = g.getPlayers().get(2);
        luis.spend(1495);
        give(g, 1, 1);
        CardEffects.apply(g, ana, birthday, null);
        check(g.getPhase() == Game.Phase.DEBT && g.getActor() == luis, "debtor is not the current player");
        check(marta.getCash() == 1500, "chain waits for the first debt");
        g.mortgage(1);
        g.payDebt();
        check(ana.getCash() == 1520 && marta.getCash() == 1490 && g.getPhase() == Game.Phase.MANAGE, "chain resumes after the debt is paid");

        // If the debtor goes bankrupt, the rest of the chain still happens and the turn does not change.
        Game h = game(Options.CLASSIC, three());
        h.getPlayers().get(1).spend(1495);
        CardEffects.apply(h, h.getPlayers().get(0), birthday, null);
        h.declareBankruptcy();
        check(h.getPlayers().get(1).isBankrupt() && h.getPlayers().get(0).getCash() == 1515 && h.getPlayers().get(2).getCash() == 1490,
                "bankruptcy mid-chain pays the creditor and the chain continues");
        check(h.getCurrent() == h.getPlayers().get(0) && h.getPhase() != Game.Phase.DEBT, "turn stays with the card's owner");

        // Paying everybody ("ronda de cañas") also chains.
        Game p = game(Options.CLASSIC, three());
        CardEffects.apply(p, p.getPlayers().get(0), new Card("x", Card.Effect.PAY_TO_ALL, 25, 0), null);
        check(p.getPlayers().get(0).getCash() == 1450 && p.getPlayers().get(1).getCash() == 1525, "pay to all");
    }

    private static void houseRulesTest() throws Exception {
        Game g = game(new Options(0, true), two());
        give(g, 1, 0);
        give(g, 3, 0);
        Street soria = (Street) g.getSquares().get(1);
        soria.setHouses(5);
        ((Street) g.getSquares().get(3)).setHouses(5);
        g.buildPool(1);
        expectRule(() -> g.buildPool(1), "second pool");
        g.buildCourt(1);
        check(soria.rent(g) == 250 + 50 + 50, "pool and court add 25x the base rent each");
        g.sellBuilding(1);
        check(!soria.hasCourt() && soria.hasPool(), "court is sold first");

        Game plain = game(Options.CLASSIC, two());
        give(plain, 1, 0);
        expectRule(() -> plain.buildPool(1), "pool without house rules");

        // Car: more than 4 rolls again; 4 or less reverses and loses two turns.
        List<PlayerSpec> car = List.of(new PlayerSpec("Ana", Avatar.COCHE, false), new PlayerSpec("Luis", Avatar.TORO, false));
        Game c = game(new Options(0, true), car, 2, 3);
        c.roll();
        check(c.getPhase() == Game.Phase.BUY, "car lands on Atocha");
        c.buy();
        check(c.getPhase() == Game.Phase.ROLL, "car rolls again after moving more than 4");

        Game d = game(new Options(0, true), car, 1, 2, 4, 6);
        d.roll();                       // 3 <= 4: back to square 37 (Barcelona)
        check(d.getPlayers().get(0).getPosition() == 37 && d.getPlayers().get(0).getSkipTurns() == 2, "car reverses and is grounded");
        d.declineBuy();
        d.passAuction();
        d.passAuction();
        d.endTurn();
        d.roll();
        d.endTurn();
        check(d.getCurrent().getName().equals("Luis") && d.getPlayers().get(0).getSkipTurns() == 1, "grounded car loses a turn");

        // Ball: 4 or less goes backwards.
        List<PlayerSpec> ball = List.of(new PlayerSpec("Ana", Avatar.PELOTA, false), new PlayerSpec("Luis", Avatar.TORO, false));
        Game b = game(new Options(0, true), ball, 1, 2);
        b.roll();
        check(b.getPlayers().get(0).getPosition() == 37, "ball goes backwards with 3");
    }

    private static void sessionTest() throws Exception {
        Session.Setup setup = new Session.Setup(7, List.of(new PlayerSpec("Ana", Avatar.COCHE, true), new PlayerSpec("Luis", null, true),
                new PlayerSpec("Marta", Avatar.PULPO, true)), new Options(0, true));
        Session s = Session.start(setup);
        for (int i = 0; i < 400 && s.game().getPhase() != Game.Phase.OVER; i++) {
            s.apply(Bot.decide(s.game()));
        }
        String saved = s.serialize();
        Session copy = Session.parse(saved);
        check(snapshot(copy.game()).equals(snapshot(s.game())), "a saved game loads back identical");

        String before = snapshot(s.game());
        String last = s.actions().get(s.size() - 1);
        s.undo();
        check(!snapshot(s.game()).equals(before) || last.isEmpty(), "undo changes the state");
        s.apply(last);
        check(snapshot(s.game()).equals(before), "replaying the undone action gives the same dice and result");
        expectRule(() -> Session.parse("basura"), "damaged save file");
    }

    private static String snapshot(Game g) {
        StringBuilder b = new StringBuilder(g.getPhase() + "/" + g.getRound() + "/" + g.getRollCount());
        for (Player p : g.getPlayers()) {
            b.append('|').append(p.getCash()).append(',').append(p.getPosition()).append(',').append(p.getProperties().size());
        }
        return b.toString();
    }
}
