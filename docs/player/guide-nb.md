# Codeling: veiledning for spillere

English: [guide-en.md](guide-en.md)

Codeling er en mod for Minecraft Java Edition. Du får en liten robot i verdenen din.
Du programmerer den i nettleseren med blokker, JavaScript eller Python.
Hver blokk du kjører, får roboten til å gjøre én ting i spillet.

Codeling er ikke laget av Microsoft, Mojang eller MakeCode-teamet. Se [Ansvarsfraskrivelse](#ansvarsfraskrivelse).

## Dette trenger du

- Minecraft Java Edition, versjon 26.3 eller 1.21.1, på en datamaskin (Windows, macOS eller Linux).
- En modlaster: Fabric Loader 0.20.1 eller nyere med Fabric API for Minecraft-versjonen din, eller NeoForge (26.3.0.102 eller nyere for 26.3, 21.1.265 eller nyere for 1.21.1).
- Codeling-filen for modlasteren og Minecraft-versjonen din (en `.jar`-fil). Navnet sier hvilken det er:

  | Minecraft | Fabric | NeoForge |
  |---|---|---|
  | 26.3 | `codeling-fabric-26.3-...jar` | `codeling-neoforge-26.3-...jar` |
  | 1.21.1 | `codeling-fabric-1.21.1-...jar` | `codeling-neoforge-1.21.1-...jar` |
- En nettleser (Chrome, Edge eller Firefox).

En forelder kan gjøre installasjonen. Den tar omtrent ti minutter. Stegene under sier 26.3; på 1.21.1 velger du 1.21.1 i installasjonsprogrammene og bruker 1.21.1-filene.

På Minecraft 1.21.1 viser redigereren også blokker og gjenstander som kom senere (for eksempel blek eik). Et program som bruker en av dem, får feilen "minecraft:... is not in this Minecraft version". Alt annet virker likt.

## Installere

1. Lukk Minecraft.
2. Gå til https://fabricmc.net/use/installer/ og last ned Fabric-installasjonsprogrammet.
3. Kjør det. Velg "Client", Minecraft-versjon 26.3, og trykk "Install".
4. Åpne Minecraft Launcher én gang og start den nye profilen "fabric-loader-26.3". Lukk spillet igjen. Da lages mappa `mods`.
5. Last ned Fabric API for 26.3 fra https://modrinth.com/mod/fabric-api.
6. Legg `.jar`-filen til Fabric API og `.jar`-filen til Codeling i mappa `mods`:
   - Windows: trykk Win+R, skriv `%appdata%\.minecraft\mods` og trykk Enter.
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
7. Start Minecraft med profilen "fabric-loader-26.3".

### Installere på NeoForge

Bruk disse stegene i stedet hvis du spiller med NeoForge. NeoForge trenger ikke Fabric API.

1. Lukk Minecraft.
2. Gå til https://neoforged.net/ og last ned NeoForge-installasjonsprogrammet for Minecraft 26.3.
3. Kjør det. Velg "Install client" og trykk "Proceed".
4. Åpne Minecraft Launcher én gang og start den nye NeoForge-profilen. Lukk spillet igjen. Da lages mappa `mods`.
5. Legg filen `codeling-neoforge-26.3-...jar` i mappa `mods` (samme mapper som i steg 6 over).
6. Start Minecraft med NeoForge-profilen.

## Åpne editoren

1. Åpne en verden (enspiller, eller en server som har Codeling, se [Flerspiller](#flerspiller)).
2. Trykk T for å åpne chatten. Skriv `/codeling` og trykk Enter.
3. Nettleseren åpner Codeling-editoren. Lenken virker bare på denne maskinen, og bare til du lukker Minecraft.
4. Mens et program kjører, fortsetter spillet når du klikker i nettleseren. Åpner du pausemenyen (Escape), går spillet på pause og roboten venter: loggen under simulatoren sier "Minecraft is paused. Go back to the game and close the menu." Lukk menyen, så fortsetter roboten.

Editoren kan vise norsk. Robotblokkene har norske navn. Noen få andre tekster er fortsatt på engelsk.

## Lag roboten

Skriv `/robot spawn` i chatten. Roboten dukker opp ved føttene dine og ser samme vei som deg.
Du har én robot. Skriver du `/robot spawn` igjen, flyttes den til deg.
Hvis verdenen tillater flere, kan du også ha roboter med navn: se [Flere roboter](#flere-roboter).

## Ditt første program

Med blokker:

1. Åpne kategorien "Robot" (grønn) i editoren.
2. Dra "robot flytt fremover med 1" ut på arbeidsflaten. Endre 1 til 3.
3. Dra "robot snu venstre" under den. Endre venstre til høyre.
4. Trykk på Kjør-knappen (play) under simulatoren til venstre.
5. Se i spillet. Roboten går 3 blokker fremover og snur seg mot høyre.

Det samme programmet i JavaScript (fanen JavaScript øverst):

```
agent.move(SixDirection.Forward, 3)
agent.turn(TurnDirection.Right)
```

I Python har navnene understrek: `agent.move_to(...)`, `agent.find_nearest(...)`.

Hver kommando venter til roboten er ferdig. Så starter den neste.
For å stoppe et program trykker du på stoppknappen i editoren, eller skriver `/robot stop` i spillet.

## Vent og gjenta

"pause (ms)" i kategorien Løkker venter før neste kommando: 1000 ms er ett sekund. "gjenta for alltid" kjører koden
inni seg om og om igjen, så lenge programmet kjører. Denne roboten holder vakt og går 3 blokker fram og tilbake
hvert andre sekund:

```
loops.forever(function () {
    agent.move(SixDirection.Forward, 3)
    agent.turn(TurnDirection.Left)
    agent.turn(TurnDirection.Left)
    loops.pause(2000)
})
```

En pause holder bare igjen koden den står i. Kode i "kjør samtidig" og i andre "gjenta for alltid"-blokker fortsetter.
Et program med "gjenta for alltid" slutter aldri av seg selv: trykk på stoppknappen i editoren, eller skriv
`/robot stop` i spillet. Begge stopper det med en gang, også midt i en pause.

## Alle robotkommandoene

Retningene gjelder fra robotens side: fremover, bakover, venstre, høyre, opp, ned.
Plassene er robotens 27 inventarplasser, nummerert fra 1 til 27.

### Bevegelse

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot flytt (retning) med (antall) | `agent.move(SixDirection.Forward, 3)` | Går 1 til 64 blokker. Stopper før hvis noe står i veien. |
| robot snu (venstre/høyre) | `agent.turn(TurnDirection.Left)` | Snur 90 grader. |
| robot tyngdekraft (på/av) | `agent.setGravity(true)` | På: roboten faller og går som en spiller. Av (slik den starter): den flyr. |
| robot gå til (posisjon) | `agent.moveTo(pos(0, 0, 5))` | Finner en vei og går dit. Svarer sann eller usann. Åpner tredører underveis. |
| robot sett hjem | `agent.setHome()` | Husker hvor roboten står, som hjemmet sitt. Også `/robot sethome`. |
| robot gå hjem | `agent.goHome()` | Går hjem som "gå til". Usann hvis den ikke har noe hjem eller ingen vei. |
| robot følg sti (av blokk) | `agent.followPath()` | Går langs stien den står på, eller en sti av blokken du velger (`agent.followPath(Block.Gravel)`), også ett trinn opp eller ned, til stien slutter eller deler seg. En robot rett ved siden av stien går opp på den; står den lenger unna, svarer den slutt og sier "I am not on the path". Høyst 256 blokker. Svarer hvorfor den stoppet: slutt, veikryss, sperret eller grense (`PathStop.Junction`). I et veikryss stopper den vendt samme vei som den kom: snu den og følg stien igjen. |
| robot følg sti av (blokk) hold (venstre/høyre) | `agent.followPath(Block.OakPlanks, TurnDirection.Left)` | Holder én hånd på veggen, slik du kan gjøre i en ekte labyrint. I hvert veikryss tar den veien lengst til venstre (eller høyre). I en blindvei med vegg foran snur den og går tilbake. Den stopper der stien slutter uten vegg foran (veien ut: slutt), tilbake der den startet (slutt), eller etter 256 blokker (grense: følg stien igjen for å gå videre). Trykk + to ganger på blokken for å velge side. |

### Bygging

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot plasser (retning) | `agent.place(SixDirection.Down)` | Setter ut blokken fra valgt plass. |
| robot ødelegg (retning) | `agent.destroy(SixDirection.Forward)` | Knuser blokken. Det som faller ut, havner i roboten. |
| robot hjelp (plasser ved flytting) (på/av) | `agent.setAssist(AgentAssist.PlaceOnMove, true)` | Legger en blokk bak seg for hvert steg. |
| robot bruk (retning) | `agent.interact(SixDirection.Forward)` | Åpner dører, luker og grinder, trykker på knapper, slår spaker. |

### Undersøke

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot oppdag (blokk/rødstein) (retning) | `agent.detect(AgentDetection.Block, SixDirection.Forward)` | Sann hvis en blokk står i veien. |
| robot undersøk (blokk/data) (retning) | `agent.inspect(AgentInspection.Block, SixDirection.Down)` | Nummeret til blokken. |
| robot er åpen (retning) | `agent.isOpen(SixDirection.Forward)` | Sann hvis en dør, luke, grind eller tønne er åpen. |
| robot oppdag (fiendtlig skapning/dyr/spiller/gjenstand) (retning) | `agent.detectEntity(EntityKind.Mob, SixDirection.Forward)` | Sann hvis en slik er i blokken ved siden av roboten. |
| robot tell i nærheten (type) innen (radius) | `agent.countNearby(EntityKind.Animal, 4)` | Hvor mange som er nær roboten, høyst 8 blokker unna. |
| robot redstonestyrke (retning) | `agent.redstonePower(SixDirection.Forward)` | Rødsteinstyrke fra 0 til 15. |
| robot posisjon | `agent.getPosition()` | Hvor roboten er. |
| robot retning | `agent.getOrientation()` | Hvilken vei den ser, i grader. |
| robot finn nærmeste (blokk) innen (radius) | `agent.findNearest(Block.OakLog, 16)` | Den nærmeste blokken av den typen, eller ingenting. |
| robot finn nærmeste (kategori) innen (radius) | `agent.findNearestCategory(BlockCategory.Logs, 16)` | Det samme for stammer, løv, malm, planker, ull eller blomster. |

### Sensorer

Radaren ser høyst 16 blokker rundt roboten. Usynlige spillere og spillere i tilskuermodus blir aldri funnet.

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot radar (type) innen (radius) | `agent.radar(EntityKind.Mob, 8)` | En liste over hvor de er, nærmeste først. Tom når det ikke er noen. Høyst de 64 nærmeste. |
| robot nærmeste (type) innen (radius) | `agent.nearestEntity(EntityKind.Player, 8)` | Hvor den nærmeste er, eller ingenting. |
| robot oppdag væske (retning) | `agent.detectLiquid(SixDirection.Down)` | Vann, lava eller ingen (`Liquid.Lava`). |
| robot er trygt (retning) | `agent.isSafe(SixDirection.Forward)` | Usann hvis blokken har lava i seg eller under seg, et fall på mer enn 3 blokker under seg, eller en fiendtlig skapning i seg eller rett ved siden av. En vegg er trygg, for roboten kan ikke gå dit. |

### Måling

Roboten blir stående og ser bare. En posisjon eller et hjørne du gir, må være innenfor 32 blokker fra roboten, ellers stopper programmet.

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot mål fritt (retning) opptil (maks) | `agent.measure(SixDirection.Forward, 32)` | Hvor mange frie blokker det er før noe er i veien. |
| robot mål vegg (retning) langs (langs) opptil (maks) | `agent.measureRun(SixDirection.Forward, SixDirection.Up, 32)` | Hvor høy eller bred veggen ved siden av er. |
| robot mål åpning (akse) opptil (maks) | `agent.measureGap(Axis.X, 32)` | Hvor bred dalen eller gangen er der roboten står. |
| robot bakkehøyde ved (posisjon) | `agent.groundHeight(pos(0, 0, 5))` | Hvor høyt bakken er der. Løv teller ikke. |
| robot høydeprofil (retning) lengde (lengde) | `agent.heightProfile(SixDirection.Forward, 16)` | En liste med bakkehøyder langs en linje. |
| robot er området fritt fra (hjørne) til (hjørne) | `agent.isAreaFree(pos(1, 0, 1), pos(5, 4, 5))` | Sann hvis ingenting er i boksen (gress og snø går fint). Høyst 32 blokker hver vei. |
| robot tell faste blokker fra (hjørne) til (hjørne) | `agent.countSolid(pos(1, 0, 1), pos(5, 4, 5))` | Hvor mange blokker som er i boksen. |
| robot finn ytterkanter fremover opptil (maks) | `agent.findBounds(32)` | De to ytre hjørnene av bygget foran, eller ingenting. |

### Inventar

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot velg plass (plass) | `agent.setSlot(1)` | Velger plassen den bygger fra. |
| robot antall i plass (plass) | `agent.getItemCount(1)` | Hvor mange ting som ligger der. |
| robot ledig i plass (plass) | `agent.getItemSpace(1)` | Hvor mange flere som får plass. |
| robot ledige plasser | `agent.freeSlots()` | Hvor mange av de 27 plassene som er tomme. |
| robot er inventaret fullt | `agent.isInventoryFull()` | Sann hvis ingen plass er tom. En stabel som ikke er full, kan fortsatt ta flere av samme ting. |
| robot gi (ting) antall (antall) i plass (plass) | `agent.setItem(Item.Stone, 64, 1)` | Gir roboten ting. Bare i kreativ modus eller med spilleregelen `codeling:free_items`. |
| robot samle alt | `agent.collectAll()` | Plukker opp ting rett ved siden av seg. |
| robot slipp (retning) fra plass (plass) antall (antall) | `agent.drop(SixDirection.Back, 1, 10)` | Slipper noen ting. |
| robot slipp alt (retning) | `agent.dropAll(SixDirection.Back)` | Slipper alt. |
| robot overfør antall (antall) fra plass (fra) til plass (til) | `agent.transfer(10, 1, 2)` | Flytter ting mellom sine egne plasser. |

### Beholdere

Kister, tønner, trakter, ovner og lignende blokker.

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot legg i beholder (retning) fra plass (plass) | `agent.storeItems(SixDirection.Forward, 0)` | Legger ting i beholderen. Plass 0 betyr alle. |
| robot ta fra beholder (retning) fra plass (plass) antall (antall) | `agent.takeItems(SixDirection.Forward, 1, 64)` | Tar fra én plass i beholderen. |
| robot ta fra (retning) (ting) antall (antall) | `agent.takeItem(SixDirection.Forward, Item.Coal, 16)` | Tar én type ting. |
| robot tell i beholder (retning) av (ting) | `agent.containerCount(SixDirection.Forward, Item.Coal)` | Hvor mange av tingen som ligger der. |
| robot beholderstørrelse (retning) | `agent.containerSize(SixDirection.Forward)` | Antall plasser, 0 hvis det ikke er en beholder. |
| robot sorter beholder (retning) | `agent.sortContainer(SixDirection.Forward)` | Rydder i kista. |

### Lage ting

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot lag (ting) antall (antall) | `agent.craft(Item.OakPlanks, 4)` | Lager ting av sine egne ting. Store oppskrifter trenger et arbeidsbord ved siden av roboten. |
| robot kan lage (ting) antall (antall) | `agent.canCraft(Item.Stick, 4)` | Sann hvis den kan lage det nå. |
| robot steinkutter lag (ting) antall (antall) | `agent.stonecut(Item.StoneBricks, 4)` | Bruker en steinkutter ved siden av roboten. |

### Tilbakemelding

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| robot si (tekst) | `agent.say("Hei")` | Viser tekst over roboten i 5 sekunder. Høyst 80 tegn. |
| robot kamera (modus) | `agent.camera(CameraMode.Follow)` | Setter robotkameraet til øyne, følg eller av (se Robotkamera under). Hvis serveren ikke tillater det, stopper programmet med en feil. |

### Posisjoner (kategorien Posisjoner)

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| ~x ~y ~z | `pos(0, 0, 5)` | Et sted målt fra føttene dine. |
| ^x ^y ^z | `posLocal(0, 0, 5)` | Et sted målt fra der du ser (z er fremover). |
| verden x y z | `world(100, 64, 200)` | Et nøyaktig sted i verdenen. |
| velg tilfeldig posisjon fra (a) til (b) | `positions.random(pos(-5, 0, -5), pos(5, 0, 5))` | Et tilfeldig sted i boksen. |
| (posisjon) x verdi | `p.getValue(Axis.X)` | Ett av tallene i en posisjon. |
| (posisjon) i verden | `p.toWorld()` | Gjør `~` eller `^` om til et nøyaktig sted. |
| (posisjon) + (annen) | `p.add(other)` | Legger sammen to posisjoner. |
| (posisjon) = (annen) | `p.equals(other)` | Sann hvis de er like. |
| (posisjon) som tekst | `p.toString()` | Posisjonen som tekst. |
| (posisjon) avstand til (annen) | `p.distanceTo(other)` | Hvor mange blokker det er mellom dem i rett linje, med desimaler. En `~`- eller `^`-posisjon mot en annen type gjøres først om til et nøyaktig sted. |
| (posisjon) - (annen) | `p.subtract(other)` | Hvor langt det er fra den andre til denne, som en posisjon av samme type. |
| (posisjon) ganger (tall) | `p.scale(2)` | Hvert tall ganget opp, rundet av til hele blokker. |
| sorter (liste) etter avstand fra (posisjon) | `positions.sortByDistance(list, p)` | En ny liste med de samme posisjonene, nærmeste først. |
| nærmeste i (liste) til (posisjon) | `positions.nearest(list, p)` | Den nærmeste posisjonen i lista, eller `null` når lista er tom. |

### Bygge stort (kategoriene Blokker, Former og Bygger)

| Blokk | JavaScript | Hva den gjør |
|---|---|---|
| fyll med (blokk) fra (a) til (b) (hvordan) | `blocks.fill(Block.Stone, pos(1, 0, 1), pos(5, 3, 5), FillOperation.Hollow)` | Fyller boksen. Hul gir vegger med luft inni, omriss bare veggene, behold fyller bare luft. Luft tømmer boksen. |
| plasser (blokk) ved (posisjon) | `blocks.place(Block.Glass, pos(0, 2, 0))` | Plasserer én blokk. |
| linje av (blokk) fra (a) til (b) | `shapes.line(Block.OakPlanks, pos(0, 0, 1), pos(5, 3, 1))` | En rett linje med blokker. |
| sirkel av (blokk) sentrum (s) radius (r) rundt (akse) (hvordan) | `shapes.circle(Block.Stone, pos(0, 0, 8), 5, Axis.Y, ShapeOperation.Outline)` | En sirkel; rundt Y ligger den flatt på bakken. |
| kule av (blokk) sentrum (s) radius (r) (hvordan) | `shapes.sphere(Block.Glass, pos(0, 6, 0), 4, ShapeOperation.Hollow)` | En kule. Hul gir en boble med luft inni. |
| labyrint ved (hjørne) bredde (b) lengde (l) vegger av (blokk) gulv av (blokk) (frø) | `shapes.maze(pos(2, 0, 2), 15, 15, Block.StoneBricks, Block.OakPlanks, 42)` | En labyrint med én vei mellom to steder, vegger 2 blokker høye, inngang på nordsiden og utgang på sørsiden. Hjørnet er i fothøyde, som hjørnet i en fill: veggene står i det laget, og gulvet erstatter bakken én blokk under. Bredde og lengde er 5 til 32; et partall bygger én blokk mindre. Samme frø gir alltid samme labyrint, så venner kan kappes gjennom labyrint 42; uten frø (trykk + på blokken for å legge til et) blir den ny hver gang. Gå gjennom den med `agent.followPath(Block.OakPlanks)`. |
| bygger flytt (retning) med (steg) | `builder.move(SixDirection.Forward, 4)` | Flytter en usynlig bygger. Den starter ved føttene dine og ser mot sør. |
| bygger snu, bygger vend mot, bygger teleporter til | `builder.turn(TurnDirection.Left)` | Snur eller flytter byggeren. |
| bygger sett merke | `builder.mark()` | Husker hvor byggeren er. |
| bygger linje fra merke med (blokk), bygger fyll fra merke med (blokk) (hvordan) | `builder.fill(Block.Stone, FillOperation.Hollow)` | Bygger fra merket til byggeren. |
| bygger plasser (blokk), bygger posisjon | `builder.place(Block.Torch)` | Plasserer en blokk der byggeren er, eller sier hvor den er. |

Roboten bygger alt dette, så den må være innenfor 32 blokker fra hver del. I overlevelse bruker den blokkene i inventaret sitt og stopper når de er brukt opp. Da sier den hvorfor, over hodet og i loggen under simulatoren (for eksempel "I have no stone in my inventory."); i kreativ modus, eller med spilleregelen `codeling:free_items`, trenger den ingen. Blokker som står i veien, blir ødelagt og havner i roboten.

Kategorien Konsoll har "si (tekst)" (`console.log("tekst")`). Den skriver i loggen under simulatoren.

### Løs en labyrint

Bygg en labyrint foran deg:

```
shapes.maze(pos(2, 0, 2), 15, 15, Block.StoneBricks, Block.OakPlanks, 42)
```

Gå inn i åpningen i nordveggen (inngangen), se sørover inn i labyrinten og skriv `/robot spawn`. Kjør så:

```
while (agent.followPath(Block.OakPlanks, TurnDirection.Left) == PathStop.Limit) {
}
agent.say("Jeg er ute!")
```

Roboten holder venstre hånd på veggen til den går ut av utgangen på sørsiden. I en stor labyrint er turen lengre enn 256 blokker, så løkka følger stien igjen hver gang den stopper ved grensen. Prøv `TurnDirection.Right`: hvilken vei er kortest i labyrint 42?

## Flere roboter

En verden kan la hver spiller ha mer enn én robot. Spillregelen `codeling:max_robots` sier hvor mange (1 til 16, og
roboten uten navn teller som én). Den er 1 fra start, så alt virker med én robot som før. En operatør skrur den opp,
for eksempel `/gamerule codeling:max_robots 4`. Skrur man den ned igjen, forsvinner ingen roboter; det kommer bare
ikke flere.

- `/robot spawn Miner` lager en robot som heter Miner, ved føttene dine. Et navn begynner med en bokstav og har høyst
  16 bokstaver, sifre, `_` eller `-` (bare a til z, ikke æ, ø og å). Robotene dine har ulike navn (Miner og miner er
  samme navn).
- Den første roboten din, den uten navn, er fortsatt standardroboten din. I programmer heter den `agent`, så gamle
  programmer og lagrede verdener virker som før.
- Hver robot har sitt eget inventar, sin egen plass, sitt eget hjem, sin egen tyngdekraft og hjelp, og lagres med
  verdenen.
- `/robot list` viser robotene dine og hvor de er. `/robot remove Miner` fjerner en: alt den bærer, havner i
  inventaret ditt, og det som ikke får plass, faller ned ved føttene dine. Roboten må være lastet inn (nær deg).
- Chattekommandoene for én robot tar navnet til slutt: `/robot move forward 3 Miner`, `/robot come Miner`,
  `/robot inventory Miner`, `/robot camera Miner`. Uten navn gjelder de standardroboten din.
- `/robot stop` stopper alle robotene og programmet ditt. `/robot stop Miner` stopper bare det Miner holder på med.

I et program er `robots.get("Miner")` roboten som heter Miner. Den kan alt `agent` kan:

```
let miner = robots.get("Miner")
control.runInParallel(function () {
    miner.move(SixDirection.Forward, 5)
    miner.destroy(SixDirection.Down)
})
agent.move(SixDirection.Back, 5)
```

Hver robot gjør én ting om gangen, og hver robot har sin egen kø av kommandoer. "kjør samtidig" lar koden inni den
kjøre samtidig med resten, så Miner og standardroboten flytter seg sammen. Uten den venter programmet på hver
kommando før den neste, som alltid.

I Blokker har kategorien Roboter "robot (navn)", "kjør samtidig" og de mest brukte robotblokkene: flytt, snu, gå til,
plasser, ødelegg, samle alt og si. Legg "robot (navn)" i en variabel og bruk variabelen i de blokkene. De andre
robotkallene (for eksempel `miner.findNearest(Block.CoalOre, 16)`) finnes i JavaScript og Python. Robotblokkene for
`agent` er som før og gjelder standardroboten din.

### Navneskilt

En robot med navn viser det over hodet. Gi den nytt navn med `/robot rename Miner Digger`, eller med et navneskilt
fra en ambolt (høyreklikk på roboten med det). Bare eieren og operatører kan gi en robot nytt navn.
`/robot rename Bob` (ett navn) gir standardroboten din et navneskilt; i programmer heter den fortsatt `agent`.

Ser du på en robot på nært hold, ser du også hvem som eier den. `/robot nametag off` skjuler navnene på alle robotene
dine, `/robot nametag on` viser dem igjen; legg til et navn for å endre bare én robot. Mens en robot snakker
(`agent.say`), tar snakkeboblen plassen til navneskiltet.

## Chattekommandoer

Et robotnavn til slutt i en kommando (vist som `[navn]`) velger en av robotene dine. Uten navn gjelder kommandoen
standardroboten din.

| Kommando | Hva den gjør |
|---|---|
| `/codeling` | Åpner editoren i nettleseren. |
| `/robot spawn [navn]` | Lager roboten din, eller flytter den til deg. Stopper et program som kjører. En ny robot med navn trenger plass under `codeling:max_robots`. |
| `/robot list` | Robotene dine og hvor de er. |
| `/robot remove <navn>` | Fjerner en robot med navn. Tingene den har, får du. |
| `/robot rename <navn> <nytt navn>` | Gir en robot nytt navn. `/robot rename <nytt navn>` gir standardroboten din et navneskilt. |
| `/robot nametag <on\|off> [navn]` | Viser eller skjuler navnet over robotene dine (uten navn: alle). |
| `/robot stop [navn]` | Uten navn: stopper alle robotene og programmet ditt. Med navn: stopper det den roboten holder på med. |
| `/robot status [navn]` | Hvor roboten er og hvilken vei den ser. |
| `/robot move <retning> <blokker> [navn]` | Går 1 til 64 blokker. `<retning>` er forward, back, left, right, up eller down. |
| `/robot turn <left\|right> [navn]` | Snur. |
| `/robot place <retning> [navn]` | Setter ut en blokk. |
| `/robot destroy <retning> [navn]` | Knuser en blokk. |
| `/robot inspect <retning> [navn]` | Viser nummeret til blokken. |
| `/robot detect <retning> [navn]` | Sann hvis en blokk står i veien. |
| `/robot slot <1-27> [navn]` | Velger en plass. |
| `/robot give <blokk> <antall> [plass] [navn]` | Gir ting, for eksempel `/robot give oak_planks 64`. Bare i kreativ modus eller med spilleregelen `codeling:free_items`. |
| `/robot inventory [navn]` | Åpner robotens inventar hvis du står nær. Ellers lister den tingene. |
| `/robot collect [navn]` | Plukker opp ting ved siden av seg. |
| `/robot drop <retning> [navn]` | Slipper alt. |
| `/robot assist <true\|false> [navn]` | Plasser ved flytting. |
| `/robot gravity <true\|false> [navn]` | Tyngdekraft på eller av. |
| `/robot sethome [navn]` | Husker hvor roboten står, som hjemmet sitt. |
| `/robot come [navn]` | Roboten går til deg. Finnes det ingen vei, hopper den til deg. Ikke fra en annen dimensjon: bruk `/robot spawn`. |
| `/robot camera [navn]` | Se med robotens øyne. Kjør den igjen, `/robot camera off` eller kameratasten tar deg tilbake til dine egne. Escape åpner pausemenyen som vanlig og lar kameraet stå på. |
| `/robot camera follow [navn]` | Følg roboten bakfra. Kjør den igjen, `/robot camera off` eller følgetasten tar deg tilbake til dine egne øyne. Escape åpner pausemenyen som vanlig og lar kameraet stå på. |
| `/robot stop all` | Stopper alle roboter. Bare for operatører. |

Kommandoene skrives på engelsk, også når editoren er på norsk.
Du kan også høyreklikke på roboten for å åpne inventaret.

### Robotkamera

`/robot camera` viser verden gjennom robotens øyne. Du kan også gi kameraet en tast: under Alternativer, Kontroller, Tastebindinger finner du "Robot camera" under Codeling (den har ingen tast fra start). Samme tast, `/robot camera` eller `/robot camera off` tar deg tilbake. Escape åpner pausemenyen (så du kan bytte til editoren) og lar kameraet stå på.

Mens du ser gjennom roboten, står kroppen din stille der den er. Øverst til venstre står "Robot camera" og navnet på roboten. Kameraet virker bare når roboten er i samme dimensjon og så nær at spillet ditt har lastet den delen av verdenen. Det slutter av seg selv når roboten fjernes, kommer for langt unna eller bytter dimensjon.

`/robot camera follow` setter i stedet kameraet bak og over roboten, så du kan se på en lang byggejobb eller rute. Kameraet snur seg med roboten. Beveg musa for å se rundt roboten, og bruk musehjulet for å komme nærmere eller lenger unna (2 til 12 blokker). Står en vegg i veien, flytter kameraet seg foran den. "Robot camera: follow" i Tastebindinger gir det en egen tast. De samme reglene gjelder som for øyne-visningen.

## Grenser

Grensene holder spillet raskt for alle.

- Én robot per spiller, med mindre spillregelen `codeling:max_robots` tillater flere (høyst 16).
- `flytt` går høyst 64 blokker per kommando. Hver blokk tar 4 spilltikk (0,2 sekunder).
- `finn nærmeste` og måleblokkene ser høyst 32 blokker ut fra roboten.
- Fyll og former bygger høyst 32 blokker ut fra roboten, høyst 32768 blokker om gangen, 256 blokker per tikk.
- `gå til` gir opp når veien er veldig lang eller ikke finnes, og svarer usann.
- Roboten virker bare i deler av verdenen som er lastet inn (nær spillere).
- Antall er høyst 64. Robotplassene er 1 til 27. Kisteplassene er 1 til 54.
- `si` viser høyst 80 tegn.
- Tyngdekraften er av fra start, så roboten svever. Slå den på med "robot tyngdekraft" eller `/robot gravity true`.
- Programmet kjører i nettleseren din. Lukker du fanen eller forlater verdenen, stopper programmet. Etter 10 sekunder uten kontakt stopper spillet roboten.

## Flerspiller

- Serveren trenger Codeling for sin modlaster (Fabric Loader og Fabric API, eller NeoForge). Alle spillere som vil programmere, trenger samme modlaster og Codeling også.
- Bare du kan programmere roboten din. Ingen andre kan sende den kommandoer. Det gjelder roboter med navn også: et
  navn velger alltid bare en av dine egne roboter.
- `/gamerule codeling:max_robots <1-16>` bestemmer hvor mange roboter hver spiller kan ha (1, standard, er én robot
  uten navn).
- Bare du og operatører på serveren kan åpne inventaret til roboten din. Andre får beskjeden "This is not your robot."
- I overlevelse samler roboten sine egne ressurser. "robot gi" og `/robot give` virker bare i kreativ modus, eller når verdenen har `/gamerule codeling:free_items true`. Ellers legger du ting i roboten gjennom inventarskjermen dens.
- Operatører kan stoppe alle roboter med `/robot stop all`.
- Spillregelen `codeling:robot_camera` bestemmer hvem som kan bruke robotkameraet: `owner` (standard: hver spiller gjennom sin egen robot), `ops` (bare operatører) eller `off` (ingen). For eksempel `/gamerule codeling:robot_camera off`. På NeoForge 26.3 viser skjermen Edit Game Rules regelen som et tekstfelt: skriv `off`, `owner` eller `ops` (på NeoForge 1.21.1 er den ikke med på skjermen; bruk kommandoen). Serveren sjekker dette, og kameraet slutter med en gang når regelen tar det bort.
- Roboten knuser og bruker ting som om den var deg. Hvis serveren beskytter et område, kan heller ikke roboten endre det.

## Feilsøking

Feilmeldingene er på engelsk.

| Problem | Hva du gjør |
|---|---|
| "Editor link expired" | Minecraft er startet på nytt siden du åpnet fanen. Skriv `/codeling` igjen. |
| "Not in a world" | Åpne en verden først, og trykk Kjør igjen. |
| "This server does not have Codeling installed" | Serveren mangler moden. Spør den som eier serveren, eller spill i enspiller. |
| Roboten gjør ingenting mens du er i nettleseren, og loggen sier "Minecraft is paused" | Pausemenyen er åpen i spillet. Gå tilbake til spillet og lukk den; roboten fortsetter av seg selv. |
| "Robot is not loaded (or not spawned: /robot spawn)" | Skriv `/robot spawn`, eller gå nærmere roboten. |
| "robot Miner is not loaded (or not spawned: /robot spawn Miner, see /robot list)" | Sjekk navnet med `/robot list`. Gå nærmere roboten, eller skriv `/robot spawn Miner`. |
| "This world allows one robot per player" | Roboter med navn trenger spillregelen `codeling:max_robots` over 1. Spør en operatør. |
| `/codeling` gjør ingenting | Et annet program bruker port 25580, ofte en annen Minecraft med Codeling. Lukk den og start Minecraft på nytt. I spillets logg står det "Codeling could not start the editor server on port 25580". |
| Blokkene ser gamle ut etter en oppdatering | Trykk Ctrl+F5 i nettleseren. |
| "Program stopped" | `/robot stop`, `/robot spawn`, en lukket fane eller at du forlot verdenen stoppet det. Trykk Kjør igjen. |

## Ansvarsfraskrivelse

Codeling er et fanprosjekt. Det har ingen tilknytning til og er ikke godkjent av Microsoft, Mojang eller Microsoft MakeCode-teamet.
Minecraft er et varemerke som tilhører Mojang Synergies AB (Microsoft).
Editoren er bygd med pxt, Microsofts verktøysett for MakeCode med åpen kildekode, som har MIT-lisens.
Codeling har selv MIT-lisens. Detaljer og lisenstekster: [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md) og [LICENSE](../../LICENSE).
