package monopoly.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import monopoly.game.Game.Options;
import monopoly.game.Game.PlayerSpec;

/**
 * Plays whole games with bots (and some with random chaos) checking the engine's invariants after every action.
 * Anything other than a RuleException thrown by the engine is a bug.
 */
public final class SimulationTest {
    private static int failures;

    private static void bug(String what) {
        if (failures++ < 15) {
            System.out.println("FAIL: " + what);
        }
    }

    private static void invariants(Game g, String ctx) {
        int houses = 0;
        int hotels = 0;
        for (Square s : g.getSquares()) {
            if (s instanceof Street st) {
                if (st.getHouses() == 5) {
                    hotels++;
                } else {
                    houses += st.getHouses();
                }
                if ((st.hasPool() || st.hasCourt()) && st.getHouses() != 5) {
                    bug("pool/court without hotel on " + st.getName() + " after " + ctx);
                }
            }
            if (s instanceof Property p && p.getOwner() != null && !p.getOwner().getProperties().contains(p)) {
                bug("owner list mismatch on " + p.getName() + " after " + ctx);
            }
            if (s instanceof Property p && p.getOwner() != null && p.getOwner().isBankrupt()) {
                bug("a bankrupt player owns " + p.getName() + " after " + ctx);
            }
        }
        if (houses + g.getHousesLeft() != 32 || hotels + g.getHotelsLeft() != 12) {
            bug("building supply out of balance after " + ctx);
        }
        for (Player p : g.getPlayers()) {
            if (p.getCash() < 0) {
                bug("negative cash for " + p.getName() + " after " + ctx);
            }
        }
    }

    private static List<PlayerSpec> specs(int n) {
        List<PlayerSpec> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new PlayerSpec("P" + i, null, true));
        }
        return list;
    }

    /** Bots play until the game ends or the action cap is hit. Returns true if it finished. */
    private static boolean botGame(long seed, int players, Options options) throws Exception {
        Session s = Session.start(new Session.Setup(seed, specs(players), options));
        for (int step = 0; step < 20000 && s.game().getPhase() != Game.Phase.OVER; step++) {
            String action = Bot.decide(s.game());
            try {
                s.apply(action);
            } catch (RuleException e) {
                s.apply(Bot.fallback(s.game()));
            }
            invariants(s.game(), action);
        }
        return s.game().getPhase() == Game.Phase.OVER;
    }

    private static void chaosGame(long seed, int players, boolean house) throws Exception {
        Random rng = new Random(seed);
        Game g = new Game(specs(players), new Options(0, house), Dice.random(rng), rng);
        String[] types = {"roll", "roll", "roll", "buy", "decline", "end", "end", "jailfine", "jailcard", "paydebt", "bankrupt",
            "pass", "accept", "reject", "cancel", "paydebt"};
        for (int step = 0; step < 3000 && g.getPhase() != Game.Phase.OVER; step++) {
            String a;
            int pos = rng.nextInt(40);
            switch (rng.nextInt(9)) {
                case 0 -> a = "build:" + pos;
                case 1 -> a = "sell:" + pos;
                case 2 -> a = "mortgage:" + pos;
                case 3 -> a = "unmortgage:" + pos;
                case 4 -> a = "bid:" + (10 + rng.nextInt(300));
                case 5 -> a = (rng.nextBoolean() ? "pool:" : "court:") + pos;
                case 6 -> a = "trade:" + rng.nextInt(players) + ":" + rng.nextInt(200) + ":" + rng.nextInt(200) + ":" + pos + ":" + rng.nextInt(40) + ":0:0";
                default -> a = types[rng.nextInt(types.length)];
            }
            if (a.equals("bankrupt") && rng.nextInt(4) != 0) {
                a = "end";
            }
            try {
                Actions.apply(g, a);
            } catch (RuleException e) {
                // expected: most random actions are illegal
            } catch (RuntimeException e) {
                bug("engine crashed on '" + a + "': " + e);
            }
            invariants(g, a);
        }
    }

    public static void main(String[] args) throws Exception {
        int finished = 0;
        int played = 0;
        for (int seed = 0; seed < 60; seed++) {
            int players = 2 + seed % 5;
            boolean house = seed % 3 == 0;
            played++;
            finished += botGame(seed, players, new Options(0, house)) ? 1 : 0;
            if (!botGame(1000 + seed, players, new Options(12, house))) {
                bug("a 12-round game did not end (seed " + (1000 + seed) + ")");
            }
        }
        for (int seed = 0; seed < 150; seed++) {
            chaosGame(seed, 2 + seed % 5, seed % 2 == 0);
        }
        System.out.println("Bot games finished: " + finished + "/" + played + " (unlimited length, capped actions)");
        System.out.println(failures == 0 ? "SimulationTest: all passed" : failures + " problem(s) found");
        System.exit(failures == 0 ? 0 : 1);
    }
}
