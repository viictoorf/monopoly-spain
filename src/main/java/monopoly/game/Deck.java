package monopoly.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** A shuffled deck: drawn cards go to the bottom, except "get out of jail free", which the player keeps. */
final class Deck {
    private final Deque<Card> cards = new ArrayDeque<>();
    private final Card jailFree;

    Deck(List<Card> source, Random rng) {
        List<Card> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled, rng);
        cards.addAll(shuffled);
        jailFree = source.stream().filter(c -> c.effect() == Card.Effect.JAIL_FREE).findFirst().orElse(null);
    }

    Card draw() {
        Card card = cards.pollFirst();
        if (card.effect() != Card.Effect.JAIL_FREE) {
            cards.addLast(card);
        }
        return card;
    }

    /** Called when a player uses (or loses) a jail card: it goes back under the deck. */
    void returnJailCard() {
        if (jailFree != null) {
            cards.addLast(jailFree);
        }
    }
}
