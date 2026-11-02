package monopoly.game;

/** A Chance / Community Chest card. Immutable data: what it says and what it does (a, b are effect parameters). */
public record Card(String text, Effect effect, int a, int b) {
    public enum Effect {
        COLLECT, PAY, MOVE_TO, MOVE_BACK, GO_TO_JAIL, JAIL_FREE, COLLECT_FROM_ALL, PAY_TO_ALL, REPAIRS,
        NEAREST_STATION, NEAREST_UTILITY
    }

    static Card of(String text, Effect effect, int a) {
        return new Card(text, effect, a, 0);
    }
}
