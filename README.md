# Monopoly España

Monopoly con tablero, cartas y tono español. Servidor en Java 17 sin librerías externas y una interfaz web
(HTML/CSS/JS sin frameworks). Evolución de un trabajo de POO de ingeniería informática, ahora como proyecto personal.

## Jugar

Necesitas un **JDK 17 o superior** (`javac -version`).

| Sistema | Comando |
|---|---|
| Linux / Mac | `./run.sh` |
| Windows | doble clic en `run.bat` |

Se abre el navegador solo. Para jugar desde el móvil en tu misma wifi: `./run.sh --lan` (o `run.bat --lan`) y abre la
dirección que se imprime. Otras opciones: `--port=9000`, `--no-browser`.

Para tener un programa con Java incluido (sin instalar nada para jugar): `build-exe.bat` en Windows genera
`dist\MonopolyEspana\MonopolyEspana.exe`; `build-exe.sh` hace lo mismo en Linux/Mac. Usa `jpackage`, que viene con el JDK.

## Qué incluye

- **Reglas oficiales**: dados y dobles, cárcel (fianza, carta, dobles), compra, alquileres, estaciones, compañías,
  casas y hoteles con construcción pareja, hipotecas, deudas y bancarrota, cartas de *Suerte* y *Junta de Vecinos*.
- **Subastas**: si no compras (o no puedes pagar), se subasta entre todos.
- **Tratos** entre jugadores: dinero, propiedades y cartas de la cárcel. Las hipotecadas cuestan el 10 % a quien las recibe.
- **Jugadores automáticos** (🤖): pujan, construyen, responden a tratos y proponen alguno para completar un color.
- **Duración**: partida completa, rápida (30 rondas) o exprés (15). Al acabar el tiempo gana el mayor patrimonio.
- **Reglas de casa** (opcionales): piscina y pista de deporte sobre un hotel (cada una suma 25 veces el alquiler base),
  coche (con más de 4 vuelve a tirar; con 4 o menos marcha atrás y 2 turnos en el taller) y balón (con 4 o menos
  retrocede; con más avanza a botes y solo se detiene en las casillas impares).
- **Guardado automático** y **deshacer**: la partida se guarda en `~/.monopoly-espana/partida.txt` tras cada jugada.
  Como el motor es determinista (semilla + lista de acciones), deshacer no permite "repetir" los dados.
- **Estadísticas** al final (patrimonio, alquileres, impuestos, construcción, vueltas, cárcel, subastas).
- Varios dispositivos a la vez con `--lan`: todos ven la partida y pueden jugar; solo el ordenador que aloja puede
  empezar, cargar o deshacer.

## Tono español

Las ciudades, las cartas (lotería de Navidad, DGT, derrama de la comunidad, el cuñado del wifi...), los impuestos
(Declaración de la Renta, IBI), la Siesta en lugar del parking y los jugadores automáticos tienen guiños propios.
Todo el texto del mapa está en `Board.java` y el de las cartas en `Cards.java`.

## Estructura

```
src/main/java/monopoly/
  Main.java                 arranque
  game/                     motor del juego (sin E/S)
    Game.java               reglas y máquina de turnos
    CardEffects.java        qué hace cada carta
    Auction.java, Trade.java
    Bot.java                jugador automático
    Actions.java            acciones en texto ("roll", "build:12", "bid:50"...)
    Session.java            partida + semilla + acciones (guardar, cargar, deshacer)
    Board.java, Cards.java  mapa y mazos
  web/                      servidor HTTP del JDK, JSON a mano, guardado en disco
src/main/resources/web/     interfaz (index.html, style.css, app.js, tipografía)
src/test/java/              GameTest, RulesTest, SimulationTest
```

## Tests

`./test.sh` (o `test.bat`) compila y ejecuta:

- `GameTest`: reglas básicas.
- `RulesTest`: subastas, tratos, deudas encadenadas, reglas de casa, guardado y deshacer.
- `SimulationTest`: partidas completas con bots y con acciones al azar, comprobando tras cada jugada que el dinero
  no es negativo, que casas y hoteles cuadran con el banco y que cada propiedad tiene un dueño coherente.

## Seguridad del servidor

Escucha solo en `localhost` salvo con `--lan`; comprueba la cabecera `Host`; exige una cabecera propia en los POST;
solo sirve una lista fija de ficheros; limita el tamaño de las peticiones y envía una política de seguridad de
contenido estricta.

## Licencias

La tipografía de los títulos es *Alfa Slab One* (SIL Open Font License 1.1), ver `FONT-LICENSE.txt`.

## Limitaciones conocidas

- Una sola mesa por servidor y sin cuentas: en `--lan` cualquier dispositivo puede actuar por cualquier jugador.
- Los jugadores automáticos los mueve el navegador del ordenador anfitrión: si lo cierras, se detienen.
- Solo el jugador en turno propone tratos.
- Las partidas largas entre 4-6 jugadores pueden alargarse mucho: para eso existen los modos rápido y exprés.
