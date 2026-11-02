package monopoly.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A trade offer between the current player ("from") and another one ("to"): cash, properties and "get out of jail"
 * cards in both directions. It stays pending until "to" accepts or rejects it (or "from" cancels it).
 * Everything is re-checked when it is accepted, because things may have changed since it was proposed.
 */
public record Trade(Player from, Player to, int cashGive, int cashAsk, List<Integer> give, List<Integer> ask,
                    int cardsGive, int cardsAsk) {

    public Trade {
        give = List.copyOf(give);
        ask = List.copyOf(ask);
    }

    /** Null if the trade can be carried out right now, otherwise the reason it cannot. */
    String problem(Game g) {
        if (from == to || from.isBankrupt() || to.isBankrupt()) {
            return "Jugador no válido para el trato.";
        }
        if (cashGive < 0 || cashAsk < 0 || cardsGive < 0 || cardsAsk < 0) {
            return "Cantidad no válida.";
        }
        if (cashGive == 0 && cashAsk == 0 && give.isEmpty() && ask.isEmpty() && cardsGive == 0 && cardsAsk == 0) {
            return "El trato está vacío.";
        }
        if (cashGive > from.getCash()) {
            return from.getName() + " no tiene tanto dinero.";
        }
        if (cashAsk > to.getCash()) {
            return to.getName() + " no tiene tanto dinero.";
        }
        if (cardsGive > from.getJailCards() || cardsAsk > to.getJailCards()) {
            return "Alguien no tiene tantas cartas de salir de la cárcel.";
        }
        for (int i : give) {
            String why = g.tradableProblem(i, from);
            if (why != null) {
                return why;
            }
        }
        for (int i : ask) {
            String why = g.tradableProblem(i, to);
            if (why != null) {
                return why;
            }
        }
        if (feeFor(g, give) > to.getCash() + cashGive - cashAsk || feeFor(g, ask) > from.getCash() + cashAsk - cashGive) {
            return "Quien reciba propiedades hipotecadas debe pagar el 10 % al banco.";
        }
        return null;
    }

    private static int feeFor(Game g, List<Integer> indexes) {
        int fee = 0;
        for (int i : indexes) {
            Property p = (Property) g.getSquares().get(i);
            fee += p.isMortgaged() ? p.getTransferFee() : 0;
        }
        return fee;
    }

    /** Carries the trade out. Call only after problem() returned null. */
    void execute(Game g) {
        from.spend(cashGive);
        to.receive(cashGive);
        to.spend(cashAsk);
        from.receive(cashAsk);
        for (int i : give) {
            g.transfer((Property) g.getSquares().get(i), from, to);
        }
        for (int i : ask) {
            g.transfer((Property) g.getSquares().get(i), to, from);
        }
        for (int k = 0; k < cardsGive; k++) {
            to.addJailCard(from.useJailCard());
        }
        for (int k = 0; k < cardsAsk; k++) {
            from.addJailCard(to.useJailCard());
        }
    }

    String describe(Game g) {
        return side(g, from, cashGive, give, cardsGive) + "  ⇄  " + side(g, to, cashAsk, ask, cardsAsk);
    }

    private static String side(Game g, Player who, int cash, List<Integer> props, int cards) {
        List<String> parts = new ArrayList<>();
        if (cash > 0) {
            parts.add(cash + " €");
        }
        for (int i : props) {
            parts.add(g.getSquares().get(i).getName());
        }
        if (cards > 0) {
            parts.add(cards + (cards == 1 ? " carta de la cárcel" : " cartas de la cárcel"));
        }
        return who.getName() + " da " + (parts.isEmpty() ? "nada" : String.join(", ", parts));
    }
}
