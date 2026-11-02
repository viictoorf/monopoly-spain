package monopoly.game;

/** Checked exception for expected, recoverable mistakes: breaking a game rule (e.g. buying without money). */
public class RuleException extends Exception {
    public RuleException(String message) {
        super(message);
    }
}
