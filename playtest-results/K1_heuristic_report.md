# Phase K-1 — Strategic Heuristic Playtest Report

**Total games:** 240
**Player model:** Utility-maximizing AI (enumerates legal actions, scores each by EV(VP) + structure synergy + risk-adjusted explore value, picks best).

## 1. Win Rates (skilled-player ceiling)

If a *good* player can't beat a difficulty, that's a balance flag. If they always win in a few turns, the difficulty isn't differentiated.

| Difficulty | Win % | Avg turns | Avg VP | VP target |
|---|---|---|---|---|
| Easy | 100% | 8.5 | 13.4 | 13 |
| Normal | 100% | 11.7 | 15.4 | 15 |
| Hard | 97% | 13.5 | 19.5 | 19 |
| Nightmare | 98% | 18.9 | 18.2 | 18 |

## 2. Decision Difficulty (does the game make you THINK?)

`gap` = top-1 utility minus top-2 utility. Small gap = the choice mattered. Big gap = one move dominates → boring.

| Difficulty | Avg gap | Median gap | High-stakes turns (gap<10) % | No-brainer turns (gap>60) % |
|---|---|---|---|---|
| Easy | 15.0 | 0.1 | 72% | 5% |
| Normal | 12.1 | 0.3 | 79% | 4% |
| Hard | 12.0 | 0.0 | 79% | 4% |
| Nightmare | 6.8 | 0.0 | 80% | 2% |

## 3. Replayability (do strategies vary?)

Average structure mix per (character × map). If a row is dominated by one structure type across ALL maps, the meta is solved.

| Character | Map | Avg lanterns | Avg outposts | Avg excavators | Avg specialists | Core Anchors |
|---|---|---|---|---|---|---|
| The Explorer | Standard | 4.0 | 1.7 | 0.4 | 2.0 | 0.00 |
| The Explorer | Crystal Caves | 3.8 | 1.4 | 0.1 | 1.6 | 0.00 |
| The Explorer | Fungal Jungle | 3.7 | 1.9 | 0.1 | 3.2 | 0.00 |
| The Prospector | Standard | 3.9 | 1.1 | 0.2 | 2.7 | 0.05 |
| The Prospector | Crystal Caves | 4.3 | 1.5 | 0.1 | 1.7 | 0.00 |
| The Prospector | Fungal Jungle | 4.0 | 2.0 | 0.1 | 3.5 | 0.00 |
| The Scout | Standard | 2.7 | 1.8 | 0.6 | 4.1 | 0.00 |
| The Scout | Crystal Caves | 3.1 | 2.1 | 0.2 | 2.6 | 0.00 |
| The Scout | Fungal Jungle | 2.7 | 2.3 | 0.4 | 4.4 | 0.00 |
| The Engineer | Standard | 5.1 | 1.0 | 0.4 | 2.5 | 0.00 |
| The Engineer | Crystal Caves | 5.2 | 0.9 | 0.3 | 1.8 | 0.15 |
| The Engineer | Fungal Jungle | 4.8 | 1.1 | 0.4 | 3.4 | 0.00 |

## 4. Dice Variance Impact

`dead roll %` = rolls that produced nothing. Catan-style games tolerate ~25% dead rolls. Higher than that and the game *feels random* even to a skilled player.

| Difficulty | Dead roll % | Avg plateau turns | Avg trades / game |
|---|---|---|---|
| Easy | 11% | 0.1 | 1.5 |
| Normal | 10% | 0.0 | 1.5 |
| Hard | 9% | 0.1 | 2.1 |
| Nightmare | 7% | 0.0 | 2.6 |

## 5. Action Variety

Average distinct action TYPES taken per game (BUILD-LANTERN, BUILD-OUTPOST, EXPLORE, TRADE, CLEAR_RUBBLE, etc).

| Difficulty | Avg distinct action types | Avg explores | Avg structures built | Avg abilities used |
|---|---|---|---|---|
| Easy | 5.8 | 4.7 | 7.6 | 14.7 |
| Normal | 5.8 | 7.0 | 8.6 | 20.2 |
| Hard | 6.3 | 8.4 | 10.2 | 26.9 |
| Nightmare | 5.2 | 11.3 | 7.5 | 23.0 |

## 6. Character × Map Win Rates

| Character | Standard | Crystal Caves | Fungal Jungle |
|---|---|---|---|
| The Explorer | 20/20 | 20/20 | 20/20 |
| The Prospector | 20/20 | 19/20 | 20/20 |
| The Scout | 20/20 | 19/20 | 19/20 |
| The Engineer | 20/20 | 20/20 | 20/20 |

## 7. Auto-detected Flags

- ⚠️ Skilled player wins 97% on Hard. May be too easy for top-end.
- ⚠️ Skilled player wins 98% on Nightmare. May be too easy for top-end.

## 8. Per-Game Summary

| # | Diff | Char | Map | Won | Turns | VP | Plateau | Avg gap |
|---|---|---|---|---|---|---|---|---|
| 1 | EASY | EXPLORER | STANDARD | ✓ | 8 | 13/13 | 0 | 35 |
| 2 | EASY | EXPLORER | STANDARD | ✓ | 15 | 13/13 | 0 | 10 |
| 3 | EASY | EXPLORER | STANDARD | ✓ | 6 | 15/13 | 0 | 17 |
| 4 | EASY | EXPLORER | STANDARD | ✓ | 7 | 13/13 | 0 | 24 |
| 5 | EASY | EXPLORER | STANDARD | ✓ | 8 | 16/13 | 0 | 5 |
| 6 | EASY | EXPLORER | CRYSTAL_ | ✓ | 8 | 13/13 | 1 | 6 |
| 7 | EASY | EXPLORER | CRYSTAL_ | ✓ | 9 | 14/13 | 1 | 13 |
| 8 | EASY | EXPLORER | CRYSTAL_ | ✓ | 5 | 13/13 | 0 | 13 |
| 9 | EASY | EXPLORER | CRYSTAL_ | ✓ | 7 | 13/13 | 0 | 14 |
| 10 | EASY | EXPLORER | CRYSTAL_ | ✓ | 8 | 13/13 | 0 | 16 |
| 11 | EASY | EXPLORER | FUNGAL_J | ✓ | 7 | 13/13 | 0 | 14 |
| 12 | EASY | EXPLORER | FUNGAL_J | ✓ | 6 | 14/13 | 0 | 14 |
| 13 | EASY | EXPLORER | FUNGAL_J | ✓ | 6 | 13/13 | 0 | 6 |
| 14 | EASY | EXPLORER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 11 |
| 15 | EASY | EXPLORER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 38 |
| 16 | EASY | PROSPECT | STANDARD | ✓ | 9 | 15/13 | 0 | 34 |
| 17 | EASY | PROSPECT | STANDARD | ✓ | 7 | 14/13 | 0 | 21 |
| 18 | EASY | PROSPECT | STANDARD | ✓ | 8 | 13/13 | 0 | 11 |
| 19 | EASY | PROSPECT | STANDARD | ✓ | 7 | 13/13 | 0 | 5 |
| 20 | EASY | PROSPECT | STANDARD | ✓ | 8 | 13/13 | 0 | 16 |
| 21 | EASY | PROSPECT | CRYSTAL_ | ✓ | 5 | 13/13 | 0 | 6 |
| 22 | EASY | PROSPECT | CRYSTAL_ | ✓ | 10 | 13/13 | 0 | 16 |
| 23 | EASY | PROSPECT | CRYSTAL_ | ✓ | 7 | 13/13 | 0 | 9 |
| 24 | EASY | PROSPECT | CRYSTAL_ | ✓ | 7 | 14/13 | 0 | 18 |
| 25 | EASY | PROSPECT | CRYSTAL_ | ✓ | 7 | 13/13 | 0 | 15 |
| 26 | EASY | PROSPECT | FUNGAL_J | ✓ | 7 | 13/13 | 0 | 12 |
| 27 | EASY | PROSPECT | FUNGAL_J | ✓ | 8 | 13/13 | 0 | 8 |
| 28 | EASY | PROSPECT | FUNGAL_J | ✓ | 8 | 13/13 | 0 | 14 |
| 29 | EASY | PROSPECT | FUNGAL_J | ✓ | 9 | 13/13 | 0 | 11 |
| 30 | EASY | PROSPECT | FUNGAL_J | ✓ | 6 | 13/13 | 0 | 9 |
| 31 | EASY | SCOUT | STANDARD | ✓ | 11 | 14/13 | 0 | 4 |
| 32 | EASY | SCOUT | STANDARD | ✓ | 11 | 13/13 | 0 | 26 |
| 33 | EASY | SCOUT | STANDARD | ✓ | 15 | 13/13 | 0 | 21 |
| 34 | EASY | SCOUT | STANDARD | ✓ | 12 | 13/13 | 0 | 8 |
| 35 | EASY | SCOUT | STANDARD | ✓ | 11 | 15/13 | 0 | 8 |
| 36 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 14/13 | 0 | 5 |
| 37 | EASY | SCOUT | CRYSTAL_ | ✓ | 18 | 13/13 | 0 | 21 |
| 38 | EASY | SCOUT | CRYSTAL_ | ✓ | 14 | 13/13 | 0 | 7 |
| 39 | EASY | SCOUT | CRYSTAL_ | ✓ | 10 | 14/13 | 0 | 14 |
| 40 | EASY | SCOUT | CRYSTAL_ | ✓ | 17 | 13/13 | 1 | 16 |
| 41 | EASY | SCOUT | FUNGAL_J | ✓ | 12 | 13/13 | 0 | 9 |
| 42 | EASY | SCOUT | FUNGAL_J | ✓ | 11 | 13/13 | 0 | 12 |
| 43 | EASY | SCOUT | FUNGAL_J | ✓ | 19 | 13/13 | 0 | 12 |
| 44 | EASY | SCOUT | FUNGAL_J | ✓ | 13 | 14/13 | 0 | 17 |
| 45 | EASY | SCOUT | FUNGAL_J | ✓ | 17 | 14/13 | 0 | 12 |
| 46 | EASY | ENGINEER | STANDARD | ✓ | 5 | 13/13 | 0 | 19 |
| 47 | EASY | ENGINEER | STANDARD | ✓ | 5 | 13/13 | 0 | 14 |
| 48 | EASY | ENGINEER | STANDARD | ✓ | 5 | 15/13 | 0 | 5 |
| 49 | EASY | ENGINEER | STANDARD | ✓ | 5 | 14/13 | 0 | 4 |
| 50 | EASY | ENGINEER | STANDARD | ✓ | 6 | 13/13 | 0 | 17 |
| 51 | EASY | ENGINEER | CRYSTAL_ | ✓ | 6 | 13/13 | 0 | 9 |
| 52 | EASY | ENGINEER | CRYSTAL_ | ✓ | 6 | 14/13 | 0 | 21 |
| 53 | EASY | ENGINEER | CRYSTAL_ | ✓ | 6 | 14/13 | 0 | 12 |
| 54 | EASY | ENGINEER | CRYSTAL_ | ✓ | 6 | 16/13 | 0 | 45 |
| 55 | EASY | ENGINEER | CRYSTAL_ | ✓ | 6 | 13/13 | 0 | 31 |
| 56 | EASY | ENGINEER | FUNGAL_J | ✓ | 6 | 13/13 | 0 | 17 |
| 57 | EASY | ENGINEER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 14 |
| 58 | EASY | ENGINEER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 17 |
| 59 | EASY | ENGINEER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 21 |
| 60 | EASY | ENGINEER | FUNGAL_J | ✓ | 5 | 13/13 | 0 | 19 |
| 61 | NORM | EXPLORER | STANDARD | ✓ | 13 | 15/15 | 0 | 21 |
| 62 | NORM | EXPLORER | STANDARD | ✓ | 9 | 15/15 | 0 | 19 |
| 63 | NORM | EXPLORER | STANDARD | ✓ | 7 | 15/15 | 0 | 2 |
| 64 | NORM | EXPLORER | STANDARD | ✓ | 13 | 15/15 | 1 | 3 |
| 65 | NORM | EXPLORER | STANDARD | ✓ | 10 | 15/15 | 0 | 4 |
| 66 | NORM | EXPLORER | CRYSTAL_ | ✓ | 16 | 15/15 | 0 | 14 |
| 67 | NORM | EXPLORER | CRYSTAL_ | ✓ | 17 | 15/15 | 0 | 10 |
| 68 | NORM | EXPLORER | CRYSTAL_ | ✓ | 15 | 15/15 | 0 | 6 |
| 69 | NORM | EXPLORER | CRYSTAL_ | ✓ | 15 | 15/15 | 0 | 1 |
| 70 | NORM | EXPLORER | CRYSTAL_ | ✓ | 12 | 15/15 | 0 | 4 |
| 71 | NORM | EXPLORER | FUNGAL_J | ✓ | 13 | 15/15 | 0 | 7 |
| 72 | NORM | EXPLORER | FUNGAL_J | ✓ | 16 | 15/15 | 0 | 4 |
| 73 | NORM | EXPLORER | FUNGAL_J | ✓ | 10 | 15/15 | 0 | 5 |
| 74 | NORM | EXPLORER | FUNGAL_J | ✓ | 12 | 15/15 | 0 | 7 |
| 75 | NORM | EXPLORER | FUNGAL_J | ✓ | 12 | 16/15 | 0 | 13 |
| 76 | NORM | PROSPECT | STANDARD | ✓ | 9 | 16/15 | 0 | 7 |
| 77 | NORM | PROSPECT | STANDARD | ✓ | 17 | 15/15 | 0 | 6 |
| 78 | NORM | PROSPECT | STANDARD | ✓ | 6 | 16/15 | 0 | 2 |
| 79 | NORM | PROSPECT | STANDARD | ✓ | 8 | 17/15 | 0 | 29 |
| 80 | NORM | PROSPECT | STANDARD | ✓ | 12 | 15/15 | 0 | 9 |
| 81 | NORM | PROSPECT | CRYSTAL_ | ✓ | 11 | 16/15 | 0 | 6 |
| 82 | NORM | PROSPECT | CRYSTAL_ | ✓ | 17 | 15/15 | 0 | 11 |
| 83 | NORM | PROSPECT | CRYSTAL_ | ✓ | 9 | 15/15 | 0 | 21 |
| 84 | NORM | PROSPECT | CRYSTAL_ | ✓ | 16 | 15/15 | 0 | 8 |
| 85 | NORM | PROSPECT | CRYSTAL_ | ✓ | 10 | 17/15 | 0 | 20 |
| 86 | NORM | PROSPECT | FUNGAL_J | ✓ | 9 | 15/15 | 0 | 5 |
| 87 | NORM | PROSPECT | FUNGAL_J | ✓ | 12 | 15/15 | 0 | 7 |
| 88 | NORM | PROSPECT | FUNGAL_J | ✓ | 13 | 15/15 | 0 | 16 |
| 89 | NORM | PROSPECT | FUNGAL_J | ✓ | 6 | 15/15 | 0 | 12 |
| 90 | NORM | PROSPECT | FUNGAL_J | ✓ | 9 | 16/15 | 0 | 23 |
| 91 | NORM | SCOUT | STANDARD | ✓ | 16 | 15/15 | 0 | 11 |
| 92 | NORM | SCOUT | STANDARD | ✓ | 16 | 15/15 | 0 | 12 |
| 93 | NORM | SCOUT | STANDARD | ✓ | 14 | 15/15 | 0 | 2 |
| 94 | NORM | SCOUT | STANDARD | ✓ | 14 | 15/15 | 0 | 4 |
| 95 | NORM | SCOUT | STANDARD | ✓ | 17 | 16/15 | 0 | 8 |
| 96 | NORM | SCOUT | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 26 |
| 97 | NORM | SCOUT | CRYSTAL_ | ✓ | 14 | 15/15 | 0 | 3 |
| 98 | NORM | SCOUT | CRYSTAL_ | ✓ | 15 | 17/15 | 0 | 9 |
| 99 | NORM | SCOUT | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 2 |
| 100 | NORM | SCOUT | CRYSTAL_ | ✓ | 15 | 15/15 | 0 | 20 |
| 101 | NORM | SCOUT | FUNGAL_J | ✓ | 15 | 18/15 | 0 | 19 |
| 102 | NORM | SCOUT | FUNGAL_J | ✓ | 18 | 16/15 | 0 | 8 |
| 103 | NORM | SCOUT | FUNGAL_J | ✓ | 16 | 15/15 | 0 | 21 |
| 104 | NORM | SCOUT | FUNGAL_J | ✓ | 16 | 15/15 | 0 | 4 |
| 105 | NORM | SCOUT | FUNGAL_J | ✓ | 17 | 16/15 | 0 | 9 |
| 106 | NORM | ENGINEER | STANDARD | ✓ | 6 | 16/15 | 0 | 22 |
| 107 | NORM | ENGINEER | STANDARD | ✓ | 10 | 18/15 | 0 | 41 |
| 108 | NORM | ENGINEER | STANDARD | ✓ | 6 | 15/15 | 0 | 38 |
| 109 | NORM | ENGINEER | STANDARD | ✓ | 7 | 15/15 | 0 | 21 |
| 110 | NORM | ENGINEER | STANDARD | ✓ | 9 | 15/15 | 0 | 22 |
| 111 | NORM | ENGINEER | CRYSTAL_ | ✓ | 6 | 15/15 | 0 | 5 |
| 112 | NORM | ENGINEER | CRYSTAL_ | ✓ | 9 | 15/15 | 0 | 8 |
| 113 | NORM | ENGINEER | CRYSTAL_ | ✓ | 7 | 15/15 | 0 | 27 |
| 114 | NORM | ENGINEER | CRYSTAL_ | ✓ | 6 | 15/15 | 0 | 3 |
| 115 | NORM | ENGINEER | CRYSTAL_ | ✓ | 7 | 15/15 | 0 | 14 |
| 116 | NORM | ENGINEER | FUNGAL_J | ✓ | 9 | 16/15 | 1 | 15 |
| 117 | NORM | ENGINEER | FUNGAL_J | ✓ | 12 | 16/15 | 0 | 8 |
| 118 | NORM | ENGINEER | FUNGAL_J | ✓ | 7 | 15/15 | 0 | 5 |
| 119 | NORM | ENGINEER | FUNGAL_J | ✓ | 7 | 15/15 | 0 | 22 |
| 120 | NORM | ENGINEER | FUNGAL_J | ✓ | 8 | 15/15 | 0 | 13 |
| 121 | HARD | EXPLORER | STANDARD | ✓ | 10 | 20/19 | 0 | 3 |
| 122 | HARD | EXPLORER | STANDARD | ✓ | 11 | 20/19 | 0 | 7 |
| 123 | HARD | EXPLORER | STANDARD | ✓ | 15 | 19/19 | 0 | 6 |
| 124 | HARD | EXPLORER | STANDARD | ✓ | 12 | 19/19 | 0 | 13 |
| 125 | HARD | EXPLORER | STANDARD | ✓ | 14 | 19/19 | 0 | 14 |
| 126 | HARD | EXPLORER | CRYSTAL_ | ✓ | 9 | 21/19 | 0 | 14 |
| 127 | HARD | EXPLORER | CRYSTAL_ | ✓ | 14 | 22/19 | 0 | 22 |
| 128 | HARD | EXPLORER | CRYSTAL_ | ✓ | 13 | 20/19 | 0 | 9 |
| 129 | HARD | EXPLORER | CRYSTAL_ | ✓ | 11 | 19/19 | 0 | 5 |
| 130 | HARD | EXPLORER | CRYSTAL_ | ✓ | 12 | 20/19 | 0 | 11 |
| 131 | HARD | EXPLORER | FUNGAL_J | ✓ | 14 | 19/19 | 0 | 10 |
| 132 | HARD | EXPLORER | FUNGAL_J | ✓ | 9 | 19/19 | 0 | 15 |
| 133 | HARD | EXPLORER | FUNGAL_J | ✓ | 17 | 19/19 | 1 | 5 |
| 134 | HARD | EXPLORER | FUNGAL_J | ✓ | 12 | 19/19 | 0 | 10 |
| 135 | HARD | EXPLORER | FUNGAL_J | ✓ | 15 | 19/19 | 0 | 8 |
| 136 | HARD | PROSPECT | STANDARD | ✓ | 11 | 19/19 | 0 | 7 |
| 137 | HARD | PROSPECT | STANDARD | ✓ | 13 | 19/19 | 0 | 14 |
| 138 | HARD | PROSPECT | STANDARD | ✓ | 12 | 21/19 | 0 | 9 |
| 139 | HARD | PROSPECT | STANDARD | ✓ | 12 | 19/19 | 0 | 14 |
| 140 | HARD | PROSPECT | STANDARD | ✓ | 20 | 19/19 | 0 | 6 |
| 141 | HARD | PROSPECT | CRYSTAL_ | ✓ | 10 | 19/19 | 0 | 18 |
| 142 | HARD | PROSPECT | CRYSTAL_ | ✓ | 9 | 19/19 | 0 | 10 |
| 143 | HARD | PROSPECT | CRYSTAL_ | ✓ | 12 | 21/19 | 0 | 16 |
| 144 | HARD | PROSPECT | CRYSTAL_ | ✓ | 12 | 19/19 | 0 | 22 |
| 145 | HARD | PROSPECT | CRYSTAL_ | ✓ | 13 | 20/19 | 0 | 8 |
| 146 | HARD | PROSPECT | FUNGAL_J | ✓ | 13 | 20/19 | 1 | 7 |
| 147 | HARD | PROSPECT | FUNGAL_J | ✓ | 12 | 20/19 | 1 | 11 |
| 148 | HARD | PROSPECT | FUNGAL_J | ✓ | 14 | 20/19 | 0 | 7 |
| 149 | HARD | PROSPECT | FUNGAL_J | ✓ | 13 | 19/19 | 0 | 4 |
| 150 | HARD | PROSPECT | FUNGAL_J | ✓ | 15 | 20/19 | 0 | 12 |
| 151 | HARD | SCOUT | STANDARD | ✓ | 20 | 20/19 | 0 | 19 |
| 152 | HARD | SCOUT | STANDARD | ✓ | 17 | 19/19 | 0 | 12 |
| 153 | HARD | SCOUT | STANDARD | ✓ | 18 | 19/19 | 0 | 25 |
| 154 | HARD | SCOUT | STANDARD | ✓ | 19 | 19/19 | 0 | 9 |
| 155 | HARD | SCOUT | STANDARD | ✓ | 20 | 19/19 | 0 | 16 |
| 156 | HARD | SCOUT | CRYSTAL_ | ✓ | 20 | 20/19 | 0 | 10 |
| 157 | HARD | SCOUT | CRYSTAL_ | ✓ | 16 | 19/19 | 0 | 14 |
| 158 | HARD | SCOUT | CRYSTAL_ | ✓ | 19 | 19/19 | 0 | 9 |
| 159 | HARD | SCOUT | CRYSTAL_ | ✓ | 19 | 19/19 | 0 | 10 |
| 160 | HARD | SCOUT | CRYSTAL_ | ✗ | 21 | 17/19 | 0 | 6 |
| 161 | HARD | SCOUT | FUNGAL_J | ✓ | 18 | 20/19 | 0 | 17 |
| 162 | HARD | SCOUT | FUNGAL_J | ✓ | 19 | 20/19 | 0 | 14 |
| 163 | HARD | SCOUT | FUNGAL_J | ✓ | 19 | 20/19 | 0 | 5 |
| 164 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 17/19 | 0 | 6 |
| 165 | HARD | SCOUT | FUNGAL_J | ✓ | 15 | 20/19 | 0 | 11 |
| 166 | HARD | ENGINEER | STANDARD | ✓ | 13 | 19/19 | 0 | 21 |
| 167 | HARD | ENGINEER | STANDARD | ✓ | 11 | 20/19 | 0 | 10 |
| 168 | HARD | ENGINEER | STANDARD | ✓ | 12 | 19/19 | 0 | 27 |
| 169 | HARD | ENGINEER | STANDARD | ✓ | 9 | 19/19 | 1 | 21 |
| 170 | HARD | ENGINEER | STANDARD | ✓ | 8 | 19/19 | 0 | 30 |
| 171 | HARD | ENGINEER | CRYSTAL_ | ✓ | 10 | 19/19 | 0 | 5 |
| 172 | HARD | ENGINEER | CRYSTAL_ | ✓ | 7 | 20/19 | 0 | 15 |
| 173 | HARD | ENGINEER | CRYSTAL_ | ✓ | 10 | 20/19 | 0 | 5 |
| 174 | HARD | ENGINEER | CRYSTAL_ | ✓ | 13 | 20/19 | 0 | 3 |
| 175 | HARD | ENGINEER | CRYSTAL_ | ✓ | 9 | 19/19 | 0 | 2 |
| 176 | HARD | ENGINEER | FUNGAL_J | ✓ | 7 | 19/19 | 0 | 7 |
| 177 | HARD | ENGINEER | FUNGAL_J | ✓ | 10 | 20/19 | 0 | 23 |
| 178 | HARD | ENGINEER | FUNGAL_J | ✓ | 8 | 21/19 | 0 | 30 |
| 179 | HARD | ENGINEER | FUNGAL_J | ✓ | 11 | 19/19 | 0 | 17 |
| 180 | HARD | ENGINEER | FUNGAL_J | ✓ | 9 | 19/19 | 0 | 7 |
| 181 | NIGH | EXPLORER | STANDARD | ✓ | 17 | 18/18 | 0 | 7 |
| 182 | NIGH | EXPLORER | STANDARD | ✓ | 18 | 19/18 | 0 | 3 |
| 183 | NIGH | EXPLORER | STANDARD | ✓ | 16 | 18/18 | 0 | 4 |
| 184 | NIGH | EXPLORER | STANDARD | ✓ | 17 | 18/18 | 0 | 6 |
| 185 | NIGH | EXPLORER | STANDARD | ✓ | 15 | 20/18 | 0 | 2 |
| 186 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 18 | 18/18 | 0 | 5 |
| 187 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 20 | 18/18 | 0 | 4 |
| 188 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 15 | 18/18 | 0 | 1 |
| 189 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 17 | 18/18 | 0 | 8 |
| 190 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 16 | 18/18 | 0 | 9 |
| 191 | NIGH | EXPLORER | FUNGAL_J | ✓ | 23 | 18/18 | 0 | 8 |
| 192 | NIGH | EXPLORER | FUNGAL_J | ✓ | 19 | 18/18 | 0 | 10 |
| 193 | NIGH | EXPLORER | FUNGAL_J | ✓ | 20 | 19/18 | 0 | 9 |
| 194 | NIGH | EXPLORER | FUNGAL_J | ✓ | 25 | 18/18 | 0 | 6 |
| 195 | NIGH | EXPLORER | FUNGAL_J | ✓ | 24 | 18/18 | 0 | 6 |
| 196 | NIGH | PROSPECT | STANDARD | ✓ | 14 | 18/18 | 0 | 4 |
| 197 | NIGH | PROSPECT | STANDARD | ✓ | 22 | 18/18 | 0 | 5 |
| 198 | NIGH | PROSPECT | STANDARD | ✓ | 18 | 18/18 | 0 | 4 |
| 199 | NIGH | PROSPECT | STANDARD | ✓ | 21 | 18/18 | 0 | 5 |
| 200 | NIGH | PROSPECT | STANDARD | ✓ | 18 | 18/18 | 0 | 11 |
| 201 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 18 | 18/18 | 0 | 1 |
| 202 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 14 | 18/18 | 0 | 1 |
| 203 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 19 | 18/18 | 0 | 7 |
| 204 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 17 | 18/18 | 0 | 4 |
| 205 | NIGH | PROSPECT | CRYSTAL_ | ✗ | 26 | 17/18 | 0 | 4 |
| 206 | NIGH | PROSPECT | FUNGAL_J | ✓ | 24 | 20/18 | 0 | 4 |
| 207 | NIGH | PROSPECT | FUNGAL_J | ✓ | 22 | 18/18 | 0 | 6 |
| 208 | NIGH | PROSPECT | FUNGAL_J | ✓ | 22 | 18/18 | 0 | 4 |
| 209 | NIGH | PROSPECT | FUNGAL_J | ✓ | 22 | 18/18 | 0 | 11 |
| 210 | NIGH | PROSPECT | FUNGAL_J | ✓ | 25 | 18/18 | 0 | 6 |
| 211 | NIGH | SCOUT | STANDARD | ✓ | 17 | 19/18 | 0 | 6 |
| 212 | NIGH | SCOUT | STANDARD | ✓ | 18 | 19/18 | 1 | 15 |
| 213 | NIGH | SCOUT | STANDARD | ✓ | 18 | 18/18 | 0 | 20 |
| 214 | NIGH | SCOUT | STANDARD | ✓ | 19 | 18/18 | 0 | 5 |
| 215 | NIGH | SCOUT | STANDARD | ✓ | 23 | 18/18 | 0 | 3 |
| 216 | NIGH | SCOUT | CRYSTAL_ | ✓ | 20 | 20/18 | 0 | 5 |
| 217 | NIGH | SCOUT | CRYSTAL_ | ✓ | 15 | 18/18 | 0 | 2 |
| 218 | NIGH | SCOUT | CRYSTAL_ | ✓ | 13 | 18/18 | 0 | 3 |
| 219 | NIGH | SCOUT | CRYSTAL_ | ✓ | 20 | 18/18 | 0 | 7 |
| 220 | NIGH | SCOUT | CRYSTAL_ | ✓ | 22 | 18/18 | 0 | 7 |
| 221 | NIGH | SCOUT | FUNGAL_J | ✓ | 18 | 18/18 | 0 | 6 |
| 222 | NIGH | SCOUT | FUNGAL_J | ✓ | 24 | 18/18 | 0 | 6 |
| 223 | NIGH | SCOUT | FUNGAL_J | ✓ | 18 | 18/18 | 0 | 5 |
| 224 | NIGH | SCOUT | FUNGAL_J | ✓ | 25 | 18/18 | 0 | 6 |
| 225 | NIGH | SCOUT | FUNGAL_J | ✓ | 20 | 18/18 | 0 | 7 |
| 226 | NIGH | ENGINEER | STANDARD | ✓ | 19 | 18/18 | 0 | 19 |
| 227 | NIGH | ENGINEER | STANDARD | ✓ | 21 | 18/18 | 0 | 10 |
| 228 | NIGH | ENGINEER | STANDARD | ✓ | 14 | 18/18 | 0 | 7 |
| 229 | NIGH | ENGINEER | STANDARD | ✓ | 19 | 18/18 | 0 | 14 |
| 230 | NIGH | ENGINEER | STANDARD | ✓ | 19 | 18/18 | 0 | 8 |
| 231 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 17 | 19/18 | 0 | 16 |
| 232 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 16 | 18/18 | 0 | 5 |
| 233 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 13 | 18/18 | 0 | 7 |
| 234 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 17 | 18/18 | 0 | 3 |
| 235 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 14 | 18/18 | 0 | 13 |
| 236 | NIGH | ENGINEER | FUNGAL_J | ✓ | 18 | 18/18 | 0 | 11 |
| 237 | NIGH | ENGINEER | FUNGAL_J | ✓ | 19 | 18/18 | 0 | 10 |
| 238 | NIGH | ENGINEER | FUNGAL_J | ✓ | 20 | 18/18 | 0 | 6 |
| 239 | NIGH | ENGINEER | FUNGAL_J | ✓ | 18 | 19/18 | 0 | 6 |
| 240 | NIGH | ENGINEER | FUNGAL_J | ✓ | 17 | 18/18 | 0 | 5 |
