package monopoly.game;

import java.util.List;
import java.util.Random;

/** Dependency-free tests (run with: ./test.sh). Dice are scripted, so every scenario is deterministic. */
public final class GameTest {
    private static int failures;

    private static Game game(int... dice) throws RuleException {
        int[] i = {0};
        return new Game(List.of("Ana", "Luis"), new Dice(() -> dice[i[0]++]), new Random(1));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static void expectRule(Runnable0 r, String what) {
        try {
            r.run();
            check(false, what + " (no exception)");
        } catch (RuleException e) {
            // expected
        }
    }

    private interface Runnable0 { void run() throws RuleException; }

    public static void main(String[] args) throws Exception {
        Game g = game();
        long streets = g.getSquares().stream().filter(Street.class::isInstance).count();
        check(g.getSquares().size() == 40 && streets == 22, "40 squares, 22 streets");
        check(g.getSquares().stream().filter(Station.class::isInstance).count() == 4, "4 stations");
        check(g.getSquares().stream().filter(Utility.class::isInstance).count() == 2, "2 utilities");

        // Buy + rent: Ana lands on Teruel (3), buys it; Luis lands there and pays the base rent (4 €).
        g = game(1, 2, 1, 2);
        g.roll();
        check(g.getPhase() == Game.Phase.BUY, "BUY phase after landing on a free street");
        g.buy();
        check(g.getPlayers().get(0).getCash() == 1440, "Ana pays 60 €");
        g.endTurn();
        g.roll();
        check(g.getPlayers().get(1).getCash() == 1496 && g.getPlayers().get(0).getCash() == 1444, "rent of 4 € transferred");

        // Salary when passing Go.
        g = game(1, 2);
        g.getPlayers().get(0).setPosition(38);
        g.roll();
        check(g.getPlayers().get(0).getCash() == 1700 && g.getPlayers().get(0).getPosition() == 1, "salary on passing Go");

        // Three doubles in a row -> jail.
        g = game(2, 2, 3, 3, 1, 1);
        g.roll();
        g.roll();
        g.roll();
        check(g.getPlayers().get(0).isInJail() && g.getPlayers().get(0).getPosition() == 10, "jail after 3 doubles");
        check(g.getPhase() == Game.Phase.MANAGE, "turn can end after jail");

        // Debt -> bankruptcy -> winner.
        g = game(2, 2);
        g.getPlayers().get(0).spend(1500);
        g.roll();
        check(g.getPhase() == Game.Phase.DEBT, "DEBT phase on a 200 € tax with no cash");
        g.declareBankruptcy();
        check(g.getPhase() == Game.Phase.OVER && "Luis".equals(g.getWinner().getName()), "winner after bankruptcy");

        // Even building + full group needed.
        Game b = game();
        Street soria = (Street) b.getSquares().get(1);
        Street teruel = (Street) b.getSquares().get(3);
        expectRule(() -> b.build(1), "build without owning the group");
        for (Street s : List.of(soria, teruel)) {
            s.setOwner(b.getPlayers().get(0));
            b.getPlayers().get(0).addProperty(s);
        }
        b.build(1);
        expectRule(() -> b.build(1), "uneven building");
        b.build(3);
        b.build(1);
        check(soria.getHouses() == 2 && teruel.getHouses() == 1 && b.getHousesLeft() == 29, "houses built evenly");
        expectRule(() -> b.mortgage(1), "mortgage with buildings in the group");

        System.out.println(failures == 0 ? "All tests passed" : failures + " test(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
