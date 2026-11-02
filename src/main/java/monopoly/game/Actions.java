package monopoly.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Text form of every player action ("roll", "build:12", "bid:50"...). The web layer, the bots, the save file and the
 * tests all speak this tiny language, so the engine has a single entry point for anything a player can do.
 */
public final class Actions {
    private Actions() { }

    public static void apply(Game g, String action) throws RuleException {
        if (action == null || action.length() > 200) {
            throw new RuleException("Acción no válida.");
        }
        String[] a = action.split(":", -1);
        switch (a[0]) {
            case "roll" -> g.roll();
            case "buy" -> g.buy();
            case "decline" -> g.declineBuy();
            case "end" -> g.endTurn();
            case "jailfine" -> g.payJailFine();
            case "jailcard" -> g.useJailCard();
            case "build" -> g.build(num(a, 1));
            case "pool" -> g.buildPool(num(a, 1));
            case "court" -> g.buildCourt(num(a, 1));
            case "sell" -> g.sellBuilding(num(a, 1));
            case "mortgage" -> g.mortgage(num(a, 1));
            case "unmortgage" -> g.unmortgage(num(a, 1));
            case "paydebt" -> g.payDebt();
            case "bankrupt" -> g.declareBankruptcy();
            case "bid" -> g.bid(num(a, 1));
            case "pass" -> g.passAuction();
            case "trade" -> g.proposeTrade(num(a, 1), num(a, 2), num(a, 3), list(a, 4), list(a, 5), num(a, 6), num(a, 7));
            case "accept" -> g.acceptTrade();
            case "reject" -> g.rejectTrade();
            case "cancel" -> g.cancelTrade();
            case "finish" -> g.finishGame();
            default -> throw new RuleException("Acción desconocida.");
        }
    }

    private static int num(String[] a, int i) throws RuleException {
        if (i >= a.length || !a[i].matches("\\d{1,6}")) {
            throw new RuleException("Dato no válido.");
        }
        return Integer.parseInt(a[i]);
    }

    private static List<Integer> list(String[] a, int i) throws RuleException {
        if (i >= a.length) {
            throw new RuleException("Dato no válido.");
        }
        List<Integer> out = new ArrayList<>();
        if (a[i].isEmpty()) {
            return out;
        }
        for (String part : a[i].split(",")) {
            if (!part.matches("\\d{1,2}")) {
                throw new RuleException("Dato no válido.");
            }
            out.add(Integer.parseInt(part));
        }
        return out;
    }
}
