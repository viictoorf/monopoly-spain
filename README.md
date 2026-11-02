# Monopoly Spain

Monopoly with a Spanish-themed board, cards and atmosphere. A Java 17 server with no external dependencies and a web interface (HTML/CSS/JS without frameworks). Originally developed as an Object-Oriented Programming (OOP) university project, now continued as a personal project.

## Play

You need **JDK 17 or later** (`javac -version`).

| System | Command |
|---|---|
| Linux / Mac | `./run.sh` |
| Windows | Double-click `run.bat` |

The browser opens automatically. To play from your phone over the same Wi-Fi network: `./run.sh --lan` (or `run.bat --lan`) and open the address displayed in the terminal. Other options: `--port=9000`, `--no-browser`.

To create a standalone application with Java included (no installation required to play): `build-exe.bat` on Windows generates `dist\MonopolyEspana\MonopolyEspana.exe`; `build-exe.sh` does the same on Linux/Mac. Both use `jpackage`, which is included with the JDK.

## Features

- **Official rules**: dice and doubles, jail (bail, get-out-of-jail card, doubles), property purchases, rent, stations, utilities, houses and hotels with even building rules, mortgages, debts and bankruptcy, *Chance* and *Community Chest* cards.
- **Auctions**: if you choose not to buy a property (or cannot afford it), it is auctioned among all players.
- **Player trades**: cash, properties and get-out-of-jail cards. Mortgaged properties cost the recipient an additional 10%.
- **AI players** (🤖): bid, build, respond to trades and propose trades to complete a color group.
- **Game duration**: full game, fast (30 rounds) or express (15 rounds). When time runs out, the player with the highest net worth wins.
- **Optional house rules**: swimming pool and sports court on top of a hotel (each adds 25 times the base rent), car (roll again on a result higher than 4; otherwise move backwards and spend 2 turns in the repair shop), and ball (on a result of 4 or lower, move backwards; otherwise bounce forward, stopping only on odd-numbered spaces).
- **Autosave and undo**: the game is saved to `~/.monopoly-espana/partida.txt` after every move. Since the game engine is deterministic (seed + action list), undo does not allow you to reroll the dice.
- **End-of-game statistics**: net worth, rent, taxes, construction, laps, jail visits and auctions.
- **Multiple devices at once with `--lan`**: everyone can view the game and play; only the host computer can start, load or undo a game.

## Spanish Theme

The cities, cards (Christmas lottery, DGT, community maintenance fees, the Wi-Fi in-law...), taxes (income tax return, property tax), *Siesta* instead of the parking space, and AI players all feature Spanish cultural references. All board text is in `Board.java`, and all card text is in `Cards.java`.

## Project Structure

```text
src/main/java/monopoly/
  Main.java                 entry point
  game/                     game engine (no I/O)
    Game.java               rules and turn state machine
    CardEffects.java        card effects
    Auction.java, Trade.java
    Bot.java                AI player
    Actions.java            text-based actions ("roll", "build:12", "bid:50"...)
    Session.java            game + seed + actions (save, load, undo)
    Board.java, Cards.java  board and card decks
  web/                      JDK HTTP server, manual JSON handling, disk storage
src/main/resources/web/     interface (index.html, style.css, app.js, font)
src/test/java/              GameTest, RulesTest, SimulationTest
```

## Tests

`./test.sh` (or `test.bat`) compiles and runs:

- `GameTest`: basic rules.
- `RulesTest`: auctions, trades, chained debts, house rules, save and undo.
- `SimulationTest`: complete games with bots and random actions, checking after every move that money never goes negative, houses and hotels match the bank's inventory, and every property has a consistent owner.

## Server Security

Listens only on `localhost` unless `--lan` is enabled; validates the `Host` header; requires a custom header for POST requests; serves only a fixed list of files; limits request sizes and sends a strict Content Security Policy.

## Licenses

The title font is *Alfa Slab One* (SIL Open Font License 1.1). See `FONT-LICENSE.txt`.

## Known Limitations

- Only one game per server and no user accounts: in `--lan` mode, any device can control any player.
- AI players are controlled by the host computer's browser. If you close it, they stop playing.
- Only the player whose turn it is can propose trades.
- Long games with 4–6 players can take a very long time; this is why fast and express modes are available.
