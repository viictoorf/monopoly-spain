package monopoly.game;

import java.util.Random;
import java.util.function.IntSupplier;

/** Two dice. The source of each die is injectable, so tests can script exact rolls. */
public final class Dice {
    /** Immutable result of one throw. */
    public record Roll(int first, int second) {
        public int sum() {
            return first + second;
        }

        public boolean isDouble() {
            return first == second;
        }
    }

    private final IntSupplier die;

    public Dice(IntSupplier die) {
        this.die = die;
    }

    public static Dice random(Random rng) {
        return new Dice(() -> rng.nextInt(6) + 1);
    }

    public Roll roll() {
        return new Roll(die.getAsInt(), die.getAsInt());
    }
}
