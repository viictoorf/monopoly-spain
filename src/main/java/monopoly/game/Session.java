package monopoly.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A game plus everything needed to rebuild it: the setup, the random seed and the list of actions played so far.
 * Because the engine is deterministic (dice and decks come from one seeded Random), replaying the actions gives back
 * exactly the same game. That is all the "save game" and "undo" features need.
 */
public final class Session {
    public record Setup(long seed, List<Game.PlayerSpec> players, Game.Options options) { }

    private static final String HEADER = "MONOPOLY-ES 1";

    private final Setup setup;
    private final List<String> actions = new ArrayList<>();
    private Game game;

    private Session(Setup setup) throws RuleException {
        this.setup = setup;
        this.game = create(setup);
    }

    private static Game create(Setup setup) throws RuleException {
        Random rng = new Random(setup.seed());
        return new Game(setup.players(), setup.options(), Dice.random(rng), rng);
    }

    public static Session start(Setup setup) throws RuleException {
        return new Session(setup);
    }

    public Game game() { return game; }
    public Setup setup() { return setup; }
    public int size() { return actions.size(); }
    public List<String> actions() { return List.copyOf(actions); }

    /** Plays an action and remembers it (only if the engine accepted it). */
    public void apply(String action) throws RuleException {
        Actions.apply(game, action);
        actions.add(action);
    }

    /** Takes back the last action. The same dice come out again if it is replayed: no re-rolling by undoing. */
    public void undo() throws RuleException {
        if (actions.isEmpty()) {
            throw new RuleException("No hay nada que deshacer.");
        }
        List<String> keep = new ArrayList<>(actions.subList(0, actions.size() - 1));
        Game fresh = create(setup);
        for (String a : keep) {
            Actions.apply(fresh, a);
        }
        game = fresh;
        actions.clear();
        actions.addAll(keep);
    }

    // ---------------------------------------------------------------- save file (plain text)

    public String serialize() {
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        b.append("seed=").append(setup.seed()).append('\n');
        b.append("rounds=").append(setup.options().maxRounds()).append('\n');
        b.append("house=").append(setup.options().houseRules() ? 1 : 0).append('\n');
        for (Game.PlayerSpec p : setup.players()) {
            b.append("player=").append(p.name()).append('|').append(p.avatar() == null ? "" : p.avatar().name())
                    .append('|').append(p.bot() ? 1 : 0).append('\n');
        }
        b.append("---\n");
        actions.forEach(a -> b.append(a).append('\n'));
        return b.toString();
    }

    /** Rebuilds a session from its save file; throws RuleException if the file is damaged or inconsistent. */
    public static Session parse(String text) throws RuleException {
        try {
            String[] lines = text.split("\n");
            if (lines.length == 0 || !lines[0].equals(HEADER)) {
                throw new RuleException("Archivo de partida no reconocido.");
            }
            long seed = 0;
            int rounds = 0;
            boolean house = false;
            List<Game.PlayerSpec> specs = new ArrayList<>();
            int i = 1;
            for (; i < lines.length && !lines[i].equals("---"); i++) {
                String[] kv = lines[i].split("=", 2);
                switch (kv[0]) {
                    case "seed" -> seed = Long.parseLong(kv[1]);
                    case "rounds" -> rounds = Integer.parseInt(kv[1]);
                    case "house" -> house = kv[1].equals("1");
                    case "player" -> {
                        String[] f = kv[1].split("\\|", -1);
                        specs.add(new Game.PlayerSpec(f[0], f[1].isEmpty() ? null : Avatar.valueOf(f[1]), f[2].equals("1")));
                    }
                    default -> throw new RuleException("Archivo de partida no reconocido.");
                }
            }
            Session s = new Session(new Setup(seed, specs, new Game.Options(rounds, house)));
            for (i++; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    s.apply(lines[i]);
                }
            }
            return s;
        } catch (RuntimeException e) {
            throw new RuleException("Archivo de partida dañado.");
        }
    }
}
