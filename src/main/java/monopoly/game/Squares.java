package monopoly.game;

/** The simple squares (no owner). Properties live in their own classes. */
public final class Squares {
    private Squares() { }

    /** Start. The salary is paid by the engine when a player passes or lands here. */
    public static final class Go extends Square {
        Go() { super(0, "Salida"); }
        @Override public String getKind() { return "go"; }
        @Override void onLand(Game game, Player player) { }
    }

    /** Jail square: landing here normally means "just visiting". */
    public static final class Jail extends Square {
        Jail() { super(Game.JAIL_POSITION, "Cárcel"); }
        @Override public String getKind() { return "jail"; }
        @Override void onLand(Game game, Player player) { }
    }

    /** Free parking, Spanish style: a nap. Nothing is collected, nothing is paid. */
    public static final class FreeParking extends Square {
        FreeParking() { super(20, "La Siesta"); }
        @Override public String getKind() { return "parking"; }
        @Override void onLand(Game game, Player player) {
            game.log(player.getName() + " se echa la siesta. Ni cobra ni paga: eso ya es ganar.");
        }
    }

    public static final class GoToJail extends Square {
        GoToJail() { super(30, "Ve a la cárcel"); }
        @Override public String getKind() { return "gotojail"; }
        @Override void onLand(Game game, Player player) { game.sendToJail(player); }
    }

    public static final class Tax extends Square {
        private final int amount;

        Tax(int index, String name, int amount) {
            super(index, name);
            this.amount = amount;
        }

        public int getAmount() { return amount; }
        @Override public String getKind() { return "tax"; }
        @Override void onLand(Game game, Player player) {
            game.charge(player, amount, null, getName(), () -> player.addTaxPaid(amount));
        }
    }

    public static final class CardSquare extends Square {
        private final boolean chance;

        CardSquare(int index, boolean chance) {
            super(index, chance ? "Suerte" : "Junta de Vecinos");
            this.chance = chance;
        }

        @Override public String getKind() { return chance ? "chance" : "chest"; }
        @Override void onLand(Game game, Player player) { game.drawCard(player, chance); }
    }
}
