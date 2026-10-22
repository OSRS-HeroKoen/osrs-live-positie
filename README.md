# Live Positie (RuneLite plugin)

**NL** – Kleine RuneLite-plugin die jouw positie in het spel (x, y, verdieping) doorgeeft aan de kaart van de app *OSRS Companion*, zodat je live ziet waar je bent.

**EN** – Small RuneLite plugin that exposes the local player's position (x, y, plane) on this computer only, so a companion app's map can show where you are.

## Wat de plugin doet / What it does

- Opens a WebSocket server on `127.0.0.1:37780` (loopback only, not reachable from other machines).
- Sends `{"type":"position","x":..,"y":..,"plane":..,"regionId":..,"world":..,"instanced":..,"timestamp":..}` on position change (and at most every 5 s while standing still), `{"type":"welcome",...}` on connect and `{"type":"logout"}` on logout or world hop.
- Accepts connections only with `Host` `127.0.0.1`/`localhost` and an `Origin` that is empty, `null`, `file://` or `http://localhost`/`127.0.0.1`. Other websites are refused (403).
- Reads nothing else from the game: no inventory, no chat, no account data. Sends nothing to the internet. Sends no data on its own initiative to anything other than local clients that connect.
- No external libraries (JDK sockets only).

## Installeren / Install

Via de **Plugin Hub** in RuneLite (moersleutel → Plugin Hub → zoek *Live Positie*), zodra de plugin daar is goedgekeurd. Werkt ook met de Jagex Launcher.

Sideloaden (`--developer-mode`) werkt **niet** met de Jagex Launcher; gebruik de Plugin Hub.

## Gebruik

1. Zet de plugin aan en log in op het spel.
2. Open OSRS Companion, ga naar de kaart en klik op **Live positie**.

## Bouwen / Build

```
./gradlew build
```

Het resultaat staat in `build/libs/osrs-live-positie.jar`.

## Licentie

BSD 2-Clause, zie `LICENSE`.
