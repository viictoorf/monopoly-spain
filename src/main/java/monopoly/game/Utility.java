package monopoly.game;

/** Utility company: rent is 4x the dice (10x if the owner has both, or after a "nearest utility" card). */
public final class Utility extends Property {
    Utility(int index, String name) {
        super(index, name, 150);
    }

    @Override public String getKind() { return "utility"; }

    @Override
    int rent(Game game) {
        boolean ten = game.rentMode() == Game.RentMode.TEN || getOwner().count(Utility.class) == 2;
        return game.lastDiceSum() * (ten ? 10 : 4);
    }
}
