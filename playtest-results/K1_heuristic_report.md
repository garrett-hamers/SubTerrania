# Phase K-1 — Strategic Heuristic Playtest Report

**Total games:** 240
**Player model:** Utility-maximizing AI (enumerates legal actions, scores each by EV(VP) + structure synergy + risk-adjusted explore value, picks best).

## 1. Win Rates (skilled-player ceiling)

If a *good* player can't beat a difficulty, that's a balance flag. If they always win in a few turns, the difficulty isn't differentiated.

| Difficulty | Win % | Avg turns | Avg VP | VP target |
|---|---|---|---|---|
| Easy | 100% | 12.2 | 13.8 | 13 |
| Normal | 73% | 15.4 | 15.0 | 15 |
| Hard | 72% | 17.9 | 18.4 | 19 |
| Nightmare | 43% | 24.6 | 20.7 | 22 |

## 2. Decision Difficulty (does the game make you THINK?)

`gap` = top-1 utility minus top-2 utility. Small gap = the choice mattered. Big gap = one move dominates → boring.

| Difficulty | Avg gap | Median gap | High-stakes turns (gap<10) % | No-brainer turns (gap>60) % |
|---|---|---|---|---|
| Easy | 16.1 | 0.7 | 73% | 5% |
| Normal | 11.1 | 0.0 | 77% | 3% |
| Hard | 12.2 | 0.0 | 77% | 3% |
| Nightmare | 6.4 | 0.0 | 82% | 1% |

## 3. Replayability (do strategies vary?)

Average structure mix per (character × map). If a row is dominated by one structure type across ALL maps, the meta is solved.

| Character | Map | Avg lanterns | Avg outposts | Avg excavators | Avg specialists | Core Anchors |
|---|---|---|---|---|---|---|
| The Explorer | Standard | 2.5 | 1.2 | 0.5 | 4.3 | 0.05 |
| The Explorer | Crystal Caves | 2.5 | 2.0 | 0.2 | 2.3 | 0.05 |
| The Explorer | Fungal Jungle | 2.1 | 1.6 | 0.3 | 5.4 | 0.00 |
| The Prospector | Standard | 2.6 | 1.6 | 0.4 | 3.7 | 0.20 |
| The Prospector | Crystal Caves | 2.6 | 1.5 | 0.2 | 3.0 | 0.05 |
| The Prospector | Fungal Jungle | 1.8 | 2.2 | 0.3 | 5.2 | 0.00 |
| The Scout | Standard | 2.6 | 1.3 | 0.5 | 3.6 | 0.10 |
| The Scout | Crystal Caves | 2.7 | 1.5 | 0.1 | 2.8 | 0.15 |
| The Scout | Fungal Jungle | 2.0 | 2.0 | 0.2 | 4.7 | 0.05 |
| The Engineer | Standard | 2.8 | 1.3 | 0.5 | 4.2 | 0.10 |
| The Engineer | Crystal Caves | 3.4 | 1.2 | 0.2 | 3.3 | 0.00 |
| The Engineer | Fungal Jungle | 2.4 | 1.4 | 0.5 | 5.1 | 0.05 |

## 4. Dice Variance Impact

`dead roll %` = rolls that produced nothing. Catan-style games tolerate ~25% dead rolls. Higher than that and the game *feels random* even to a skilled player.

| Difficulty | Dead roll % | Avg plateau turns | Avg trades / game |
|---|---|---|---|
| Easy | 14% | 1.2 | 1.0 |
| Normal | 14% | 1.4 | 1.3 |
| Hard | 13% | 1.7 | 1.2 |
| Nightmare | 7% | 1.8 | 1.7 |

## 5. Action Variety

Average distinct action TYPES taken per game (BUILD-LANTERN, BUILD-OUTPOST, EXPLORE, TRADE, CLEAR_RUBBLE, etc).

| Difficulty | Avg distinct action types | Avg explores | Avg structures built | Avg abilities used |
|---|---|---|---|---|
| Easy | 6.0 | 4.4 | 7.7 | 8.7 |
| Normal | 5.9 | 6.3 | 8.4 | 11.0 |
| Hard | 6.2 | 8.0 | 9.3 | 12.4 |
| Nightmare | 5.9 | 14.2 | 8.0 | 0.1 |

## 6. Character × Map Win Rates

| Character | Standard | Crystal Caves | Fungal Jungle |
|---|---|---|---|
| The Explorer | 16/20 | 17/20 | 13/20 |
| The Prospector | 15/20 | 14/20 | 11/20 |
| The Scout | 14/20 | 13/20 | 9/20 |
| The Engineer | 18/20 | 17/20 | 16/20 |

## 7. Auto-detected Flags

- ✅ No automated red flags surfaced.

## 8. Per-Game Summary

| # | Diff | Char | Map | Won | Turns | VP | Plateau | Avg gap |
|---|---|---|---|---|---|---|---|---|
| 1 | EASY | EXPLORER | STANDARD | ✓ | 10 | 14/13 | 0 | 13 |
| 2 | EASY | EXPLORER | STANDARD | ✓ | 14 | 13/13 | 2 | 15 |
| 3 | EASY | EXPLORER | STANDARD | ✓ | 10 | 13/13 | 2 | 16 |
| 4 | EASY | EXPLORER | STANDARD | ✓ | 9 | 13/13 | 2 | 21 |
| 5 | EASY | EXPLORER | STANDARD | ✓ | 11 | 15/13 | 1 | 26 |
| 6 | EASY | EXPLORER | CRYSTAL_ | ✓ | 14 | 13/13 | 0 | 2 |
| 7 | EASY | EXPLORER | CRYSTAL_ | ✓ | 9 | 14/13 | 0 | 9 |
| 8 | EASY | EXPLORER | CRYSTAL_ | ✓ | 14 | 13/13 | 0 | 6 |
| 9 | EASY | EXPLORER | CRYSTAL_ | ✓ | 13 | 13/13 | 2 | 4 |
| 10 | EASY | EXPLORER | CRYSTAL_ | ✓ | 14 | 13/13 | 2 | 9 |
| 11 | EASY | EXPLORER | FUNGAL_J | ✓ | 13 | 14/13 | 1 | 37 |
| 12 | EASY | EXPLORER | FUNGAL_J | ✓ | 10 | 13/13 | 0 | 10 |
| 13 | EASY | EXPLORER | FUNGAL_J | ✓ | 15 | 14/13 | 2 | 7 |
| 14 | EASY | EXPLORER | FUNGAL_J | ✓ | 11 | 13/13 | 2 | 14 |
| 15 | EASY | EXPLORER | FUNGAL_J | ✓ | 14 | 14/13 | 1 | 6 |
| 16 | EASY | PROSPECT | STANDARD | ✓ | 13 | 13/13 | 1 | 4 |
| 17 | EASY | PROSPECT | STANDARD | ✓ | 12 | 13/13 | 1 | 16 |
| 18 | EASY | PROSPECT | STANDARD | ✓ | 13 | 13/13 | 0 | 7 |
| 19 | EASY | PROSPECT | STANDARD | ✓ | 9 | 13/13 | 1 | 4 |
| 20 | EASY | PROSPECT | STANDARD | ✓ | 14 | 14/13 | 2 | 24 |
| 21 | EASY | PROSPECT | CRYSTAL_ | ✓ | 10 | 13/13 | 0 | 7 |
| 22 | EASY | PROSPECT | CRYSTAL_ | ✓ | 17 | 14/13 | 3 | 5 |
| 23 | EASY | PROSPECT | CRYSTAL_ | ✓ | 9 | 16/13 | 0 | 21 |
| 24 | EASY | PROSPECT | CRYSTAL_ | ✓ | 12 | 15/13 | 0 | 9 |
| 25 | EASY | PROSPECT | CRYSTAL_ | ✓ | 9 | 17/13 | 1 | 13 |
| 26 | EASY | PROSPECT | FUNGAL_J | ✓ | 15 | 14/13 | 1 | 26 |
| 27 | EASY | PROSPECT | FUNGAL_J | ✓ | 8 | 13/13 | 0 | 7 |
| 28 | EASY | PROSPECT | FUNGAL_J | ✓ | 10 | 13/13 | 0 | 7 |
| 29 | EASY | PROSPECT | FUNGAL_J | ✓ | 14 | 13/13 | 1 | 18 |
| 30 | EASY | PROSPECT | FUNGAL_J | ✓ | 8 | 13/13 | 0 | 12 |
| 31 | EASY | SCOUT | STANDARD | ✓ | 10 | 13/13 | 0 | 16 |
| 32 | EASY | SCOUT | STANDARD | ✓ | 12 | 14/13 | 1 | 14 |
| 33 | EASY | SCOUT | STANDARD | ✓ | 14 | 15/13 | 0 | 19 |
| 34 | EASY | SCOUT | STANDARD | ✓ | 14 | 14/13 | 3 | 10 |
| 35 | EASY | SCOUT | STANDARD | ✓ | 11 | 13/13 | 1 | 17 |
| 36 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 13/13 | 0 | 17 |
| 37 | EASY | SCOUT | CRYSTAL_ | ✓ | 19 | 17/13 | 1 | 8 |
| 38 | EASY | SCOUT | CRYSTAL_ | ✓ | 15 | 13/13 | 2 | 10 |
| 39 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 14/13 | 1 | 20 |
| 40 | EASY | SCOUT | CRYSTAL_ | ✓ | 11 | 13/13 | 0 | 12 |
| 41 | EASY | SCOUT | FUNGAL_J | ✓ | 14 | 15/13 | 2 | 39 |
| 42 | EASY | SCOUT | FUNGAL_J | ✓ | 15 | 13/13 | 2 | 11 |
| 43 | EASY | SCOUT | FUNGAL_J | ✓ | 17 | 15/13 | 1 | 2 |
| 44 | EASY | SCOUT | FUNGAL_J | ✓ | 16 | 19/13 | 1 | 19 |
| 45 | EASY | SCOUT | FUNGAL_J | ✓ | 20 | 13/13 | 4 | 5 |
| 46 | EASY | ENGINEER | STANDARD | ✓ | 10 | 13/13 | 1 | 22 |
| 47 | EASY | ENGINEER | STANDARD | ✓ | 11 | 13/13 | 3 | 16 |
| 48 | EASY | ENGINEER | STANDARD | ✓ | 12 | 13/13 | 3 | 44 |
| 49 | EASY | ENGINEER | STANDARD | ✓ | 8 | 14/13 | 1 | 27 |
| 50 | EASY | ENGINEER | STANDARD | ✓ | 15 | 13/13 | 2 | 7 |
| 51 | EASY | ENGINEER | CRYSTAL_ | ✓ | 8 | 14/13 | 0 | 17 |
| 52 | EASY | ENGINEER | CRYSTAL_ | ✓ | 11 | 14/13 | 2 | 28 |
| 53 | EASY | ENGINEER | CRYSTAL_ | ✓ | 11 | 15/13 | 2 | 10 |
| 54 | EASY | ENGINEER | CRYSTAL_ | ✓ | 11 | 13/13 | 1 | 7 |
| 55 | EASY | ENGINEER | CRYSTAL_ | ✓ | 10 | 13/13 | 1 | 27 |
| 56 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 14/13 | 1 | 39 |
| 57 | EASY | ENGINEER | FUNGAL_J | ✓ | 13 | 15/13 | 2 | 13 |
| 58 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 14/13 | 2 | 51 |
| 59 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 13/13 | 2 | 35 |
| 60 | EASY | ENGINEER | FUNGAL_J | ✓ | 10 | 13/13 | 1 | 30 |
| 61 | NORM | EXPLORER | STANDARD | ✗ | 19 | 12/15 | 4 | 11 |
| 62 | NORM | EXPLORER | STANDARD | ✓ | 14 | 18/15 | 2 | 25 |
| 63 | NORM | EXPLORER | STANDARD | ✓ | 13 | 15/15 | 2 | 10 |
| 64 | NORM | EXPLORER | STANDARD | ✓ | 14 | 16/15 | 0 | 19 |
| 65 | NORM | EXPLORER | STANDARD | ✓ | 13 | 18/15 | 1 | 13 |
| 66 | NORM | EXPLORER | CRYSTAL_ | ✓ | 11 | 15/15 | 0 | 7 |
| 67 | NORM | EXPLORER | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 8 |
| 68 | NORM | EXPLORER | CRYSTAL_ | ✓ | 16 | 16/15 | 3 | 3 |
| 69 | NORM | EXPLORER | CRYSTAL_ | ✓ | 13 | 15/15 | 2 | 16 |
| 70 | NORM | EXPLORER | CRYSTAL_ | ✓ | 15 | 16/15 | 1 | 9 |
| 71 | NORM | EXPLORER | FUNGAL_J | ✓ | 13 | 15/15 | 0 | 7 |
| 72 | NORM | EXPLORER | FUNGAL_J | ✓ | 14 | 15/15 | 0 | 17 |
| 73 | NORM | EXPLORER | FUNGAL_J | ✓ | 18 | 15/15 | 2 | 10 |
| 74 | NORM | EXPLORER | FUNGAL_J | ✓ | 14 | 15/15 | 0 | 13 |
| 75 | NORM | EXPLORER | FUNGAL_J | ✗ | 19 | 14/15 | 2 | 10 |
| 76 | NORM | PROSPECT | STANDARD | ✓ | 16 | 15/15 | 2 | 10 |
| 77 | NORM | PROSPECT | STANDARD | ✓ | 13 | 17/15 | 0 | 14 |
| 78 | NORM | PROSPECT | STANDARD | ✓ | 12 | 15/15 | 1 | 7 |
| 79 | NORM | PROSPECT | STANDARD | ✓ | 16 | 20/15 | 1 | 4 |
| 80 | NORM | PROSPECT | STANDARD | ✗ | 19 | 12/15 | 3 | 11 |
| 81 | NORM | PROSPECT | CRYSTAL_ | ✗ | 19 | 14/15 | 3 | 7 |
| 82 | NORM | PROSPECT | CRYSTAL_ | ✓ | 15 | 15/15 | 2 | 8 |
| 83 | NORM | PROSPECT | CRYSTAL_ | ✓ | 14 | 16/15 | 0 | 7 |
| 84 | NORM | PROSPECT | CRYSTAL_ | ✓ | 15 | 15/15 | 1 | 9 |
| 85 | NORM | PROSPECT | CRYSTAL_ | ✓ | 15 | 17/15 | 1 | 6 |
| 86 | NORM | PROSPECT | FUNGAL_J | ✗ | 19 | 14/15 | 3 | 21 |
| 87 | NORM | PROSPECT | FUNGAL_J | ✗ | 19 | 14/15 | 3 | 12 |
| 88 | NORM | PROSPECT | FUNGAL_J | ✓ | 15 | 16/15 | 0 | 18 |
| 89 | NORM | PROSPECT | FUNGAL_J | ✓ | 15 | 15/15 | 0 | 10 |
| 90 | NORM | PROSPECT | FUNGAL_J | ✗ | 19 | 14/15 | 2 | 14 |
| 91 | NORM | SCOUT | STANDARD | ✓ | 16 | 15/15 | 4 | 38 |
| 92 | NORM | SCOUT | STANDARD | ✓ | 15 | 16/15 | 0 | 17 |
| 93 | NORM | SCOUT | STANDARD | ✓ | 13 | 15/15 | 0 | 37 |
| 94 | NORM | SCOUT | STANDARD | ✗ | 19 | 12/15 | 3 | 11 |
| 95 | NORM | SCOUT | STANDARD | ✗ | 19 | 13/15 | 3 | 4 |
| 96 | NORM | SCOUT | CRYSTAL_ | ✓ | 12 | 17/15 | 0 | 9 |
| 97 | NORM | SCOUT | CRYSTAL_ | ✓ | 16 | 15/15 | 3 | 5 |
| 98 | NORM | SCOUT | CRYSTAL_ | ✓ | 16 | 15/15 | 0 | 2 |
| 99 | NORM | SCOUT | CRYSTAL_ | ✗ | 19 | 14/15 | 3 | 9 |
| 100 | NORM | SCOUT | CRYSTAL_ | ✓ | 18 | 15/15 | 4 | 9 |
| 101 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 13/15 | 0 | 12 |
| 102 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 10/15 | 3 | 7 |
| 103 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 14/15 | 2 | 9 |
| 104 | NORM | SCOUT | FUNGAL_J | ✓ | 17 | 15/15 | 0 | 9 |
| 105 | NORM | SCOUT | FUNGAL_J | ✓ | 17 | 15/15 | 2 | 6 |
| 106 | NORM | ENGINEER | STANDARD | ✓ | 12 | 16/15 | 0 | 30 |
| 107 | NORM | ENGINEER | STANDARD | ✓ | 15 | 15/15 | 0 | 11 |
| 108 | NORM | ENGINEER | STANDARD | ✗ | 19 | 12/15 | 2 | 11 |
| 109 | NORM | ENGINEER | STANDARD | ✓ | 9 | 15/15 | 0 | 11 |
| 110 | NORM | ENGINEER | STANDARD | ✓ | 16 | 16/15 | 0 | 3 |
| 111 | NORM | ENGINEER | CRYSTAL_ | ✓ | 9 | 16/15 | 0 | 12 |
| 112 | NORM | ENGINEER | CRYSTAL_ | ✓ | 15 | 15/15 | 3 | 12 |
| 113 | NORM | ENGINEER | CRYSTAL_ | ✓ | 14 | 16/15 | 1 | 3 |
| 114 | NORM | ENGINEER | CRYSTAL_ | ✓ | 16 | 16/15 | 2 | 9 |
| 115 | NORM | ENGINEER | CRYSTAL_ | ✓ | 14 | 15/15 | 0 | 4 |
| 116 | NORM | ENGINEER | FUNGAL_J | ✗ | 19 | 13/15 | 2 | 9 |
| 117 | NORM | ENGINEER | FUNGAL_J | ✓ | 12 | 15/15 | 1 | 14 |
| 118 | NORM | ENGINEER | FUNGAL_J | ✓ | 8 | 16/15 | 0 | 6 |
| 119 | NORM | ENGINEER | FUNGAL_J | ✓ | 12 | 15/15 | 1 | 9 |
| 120 | NORM | ENGINEER | FUNGAL_J | ✗ | 19 | 14/15 | 2 | 4 |
| 121 | HARD | EXPLORER | STANDARD | ✓ | 20 | 20/19 | 2 | 30 |
| 122 | HARD | EXPLORER | STANDARD | ✓ | 20 | 19/19 | 5 | 10 |
| 123 | HARD | EXPLORER | STANDARD | ✓ | 18 | 19/19 | 1 | 7 |
| 124 | HARD | EXPLORER | STANDARD | ✓ | 17 | 19/19 | 1 | 7 |
| 125 | HARD | EXPLORER | STANDARD | ✓ | 18 | 19/19 | 1 | 16 |
| 126 | HARD | EXPLORER | CRYSTAL_ | ✗ | 21 | 16/19 | 2 | 3 |
| 127 | HARD | EXPLORER | CRYSTAL_ | ✓ | 12 | 19/19 | 0 | 11 |
| 128 | HARD | EXPLORER | CRYSTAL_ | ✓ | 17 | 20/19 | 0 | 14 |
| 129 | HARD | EXPLORER | CRYSTAL_ | ✓ | 17 | 19/19 | 0 | 3 |
| 130 | HARD | EXPLORER | CRYSTAL_ | ✓ | 17 | 21/19 | 0 | 14 |
| 131 | HARD | EXPLORER | FUNGAL_J | ✗ | 21 | 14/19 | 4 | 10 |
| 132 | HARD | EXPLORER | FUNGAL_J | ✓ | 20 | 19/19 | 2 | 11 |
| 133 | HARD | EXPLORER | FUNGAL_J | ✗ | 21 | 16/19 | 3 | 13 |
| 134 | HARD | EXPLORER | FUNGAL_J | ✗ | 21 | 16/19 | 3 | 8 |
| 135 | HARD | EXPLORER | FUNGAL_J | ✓ | 19 | 20/19 | 0 | 16 |
| 136 | HARD | PROSPECT | STANDARD | ✓ | 17 | 19/19 | 0 | 7 |
| 137 | HARD | PROSPECT | STANDARD | ✗ | 21 | 17/19 | 1 | 16 |
| 138 | HARD | PROSPECT | STANDARD | ✓ | 13 | 19/19 | 0 | 10 |
| 139 | HARD | PROSPECT | STANDARD | ✓ | 20 | 19/19 | 4 | 5 |
| 140 | HARD | PROSPECT | STANDARD | ✓ | 13 | 19/19 | 0 | 6 |
| 141 | HARD | PROSPECT | CRYSTAL_ | ✗ | 21 | 17/19 | 6 | 14 |
| 142 | HARD | PROSPECT | CRYSTAL_ | ✓ | 14 | 20/19 | 2 | 11 |
| 143 | HARD | PROSPECT | CRYSTAL_ | ✗ | 21 | 13/19 | 3 | 8 |
| 144 | HARD | PROSPECT | CRYSTAL_ | ✗ | 21 | 17/19 | 3 | 5 |
| 145 | HARD | PROSPECT | CRYSTAL_ | ✓ | 19 | 20/19 | 1 | 8 |
| 146 | HARD | PROSPECT | FUNGAL_J | ✗ | 21 | 15/19 | 4 | 15 |
| 147 | HARD | PROSPECT | FUNGAL_J | ✗ | 21 | 18/19 | 4 | 11 |
| 148 | HARD | PROSPECT | FUNGAL_J | ✓ | 17 | 19/19 | 1 | 6 |
| 149 | HARD | PROSPECT | FUNGAL_J | ✓ | 19 | 19/19 | 1 | 8 |
| 150 | HARD | PROSPECT | FUNGAL_J | ✓ | 16 | 19/19 | 2 | 12 |
| 151 | HARD | SCOUT | STANDARD | ✓ | 18 | 19/19 | 1 | 6 |
| 152 | HARD | SCOUT | STANDARD | ✓ | 18 | 19/19 | 0 | 7 |
| 153 | HARD | SCOUT | STANDARD | ✓ | 15 | 20/19 | 3 | 14 |
| 154 | HARD | SCOUT | STANDARD | ✓ | 20 | 19/19 | 1 | 9 |
| 155 | HARD | SCOUT | STANDARD | ✓ | 18 | 19/19 | 0 | 12 |
| 156 | HARD | SCOUT | CRYSTAL_ | ✓ | 18 | 19/19 | 1 | 8 |
| 157 | HARD | SCOUT | CRYSTAL_ | ✓ | 19 | 19/19 | 1 | 12 |
| 158 | HARD | SCOUT | CRYSTAL_ | ✗ | 21 | 16/19 | 3 | 5 |
| 159 | HARD | SCOUT | CRYSTAL_ | ✗ | 21 | 17/19 | 0 | 10 |
| 160 | HARD | SCOUT | CRYSTAL_ | ✓ | 12 | 19/19 | 0 | 15 |
| 161 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 14/19 | 5 | 17 |
| 162 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 17/19 | 4 | 7 |
| 163 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 18/19 | 2 | 11 |
| 164 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 13/19 | 4 | 14 |
| 165 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 15/19 | 5 | 19 |
| 166 | HARD | ENGINEER | STANDARD | ✓ | 17 | 23/19 | 1 | 58 |
| 167 | HARD | ENGINEER | STANDARD | ✓ | 18 | 19/19 | 2 | 11 |
| 168 | HARD | ENGINEER | STANDARD | ✓ | 14 | 19/19 | 1 | 20 |
| 169 | HARD | ENGINEER | STANDARD | ✓ | 13 | 19/19 | 0 | 4 |
| 170 | HARD | ENGINEER | STANDARD | ✓ | 16 | 20/19 | 1 | 29 |
| 171 | HARD | ENGINEER | CRYSTAL_ | ✓ | 14 | 20/19 | 0 | 4 |
| 172 | HARD | ENGINEER | CRYSTAL_ | ✓ | 19 | 21/19 | 0 | 9 |
| 173 | HARD | ENGINEER | CRYSTAL_ | ✓ | 14 | 20/19 | 1 | 23 |
| 174 | HARD | ENGINEER | CRYSTAL_ | ✓ | 14 | 19/19 | 0 | 8 |
| 175 | HARD | ENGINEER | CRYSTAL_ | ✓ | 17 | 19/19 | 2 | 5 |
| 176 | HARD | ENGINEER | FUNGAL_J | ✓ | 14 | 19/19 | 1 | 14 |
| 177 | HARD | ENGINEER | FUNGAL_J | ✓ | 17 | 20/19 | 0 | 21 |
| 178 | HARD | ENGINEER | FUNGAL_J | ✓ | 15 | 19/19 | 2 | 24 |
| 179 | HARD | ENGINEER | FUNGAL_J | ✓ | 14 | 19/19 | 1 | 11 |
| 180 | HARD | ENGINEER | FUNGAL_J | ✓ | 20 | 20/19 | 1 | 13 |
| 181 | NIGH | EXPLORER | STANDARD | ✓ | 25 | 22/22 | 0 | 7 |
| 182 | NIGH | EXPLORER | STANDARD | ✗ | 26 | 21/22 | 1 | 1 |
| 183 | NIGH | EXPLORER | STANDARD | ✗ | 26 | 21/22 | 4 | 10 |
| 184 | NIGH | EXPLORER | STANDARD | ✗ | 26 | 21/22 | 3 | 8 |
| 185 | NIGH | EXPLORER | STANDARD | ✓ | 21 | 22/22 | 1 | 8 |
| 186 | NIGH | EXPLORER | CRYSTAL_ | ✗ | 26 | 19/22 | 4 | 6 |
| 187 | NIGH | EXPLORER | CRYSTAL_ | ✗ | 26 | 20/22 | 1 | 2 |
| 188 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 20 | 22/22 | 1 | 4 |
| 189 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 22 | 23/22 | 1 | 5 |
| 190 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 25 | 22/22 | 0 | 4 |
| 191 | NIGH | EXPLORER | FUNGAL_J | ✓ | 24 | 22/22 | 2 | 8 |
| 192 | NIGH | EXPLORER | FUNGAL_J | ✓ | 22 | 22/22 | 3 | 6 |
| 193 | NIGH | EXPLORER | FUNGAL_J | ✗ | 26 | 19/22 | 2 | 7 |
| 194 | NIGH | EXPLORER | FUNGAL_J | ✗ | 26 | 21/22 | 2 | 7 |
| 195 | NIGH | EXPLORER | FUNGAL_J | ✗ | 26 | 18/22 | 2 | 5 |
| 196 | NIGH | PROSPECT | STANDARD | ✓ | 24 | 22/22 | 1 | 11 |
| 197 | NIGH | PROSPECT | STANDARD | ✗ | 26 | 19/22 | 3 | 7 |
| 198 | NIGH | PROSPECT | STANDARD | ✗ | 26 | 20/22 | 2 | 4 |
| 199 | NIGH | PROSPECT | STANDARD | ✗ | 26 | 20/22 | 2 | 10 |
| 200 | NIGH | PROSPECT | STANDARD | ✓ | 23 | 23/22 | 2 | 4 |
| 201 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 25 | 22/22 | 2 | 5 |
| 202 | NIGH | PROSPECT | CRYSTAL_ | ✗ | 26 | 15/22 | 2 | 7 |
| 203 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 23 | 23/22 | 2 | 8 |
| 204 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 21 | 22/22 | 2 | 3 |
| 205 | NIGH | PROSPECT | CRYSTAL_ | ✗ | 26 | 21/22 | 0 | 4 |
| 206 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 20/22 | 2 | 3 |
| 207 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 18/22 | 2 | 6 |
| 208 | NIGH | PROSPECT | FUNGAL_J | ✓ | 22 | 22/22 | 1 | 3 |
| 209 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 21/22 | 3 | 11 |
| 210 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 21/22 | 2 | 8 |
| 211 | NIGH | SCOUT | STANDARD | ✗ | 26 | 20/22 | 1 | 4 |
| 212 | NIGH | SCOUT | STANDARD | ✓ | 19 | 22/22 | 0 | 3 |
| 213 | NIGH | SCOUT | STANDARD | ✗ | 26 | 20/22 | 1 | 11 |
| 214 | NIGH | SCOUT | STANDARD | ✗ | 26 | 21/22 | 2 | 6 |
| 215 | NIGH | SCOUT | STANDARD | ✗ | 26 | 21/22 | 1 | 4 |
| 216 | NIGH | SCOUT | CRYSTAL_ | ✓ | 25 | 23/22 | 1 | 3 |
| 217 | NIGH | SCOUT | CRYSTAL_ | ✗ | 26 | 16/22 | 2 | 5 |
| 218 | NIGH | SCOUT | CRYSTAL_ | ✗ | 26 | 21/22 | 1 | 4 |
| 219 | NIGH | SCOUT | CRYSTAL_ | ✗ | 26 | 18/22 | 2 | 3 |
| 220 | NIGH | SCOUT | CRYSTAL_ | ✗ | 26 | 18/22 | 3 | 5 |
| 221 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 17/22 | 3 | 4 |
| 222 | NIGH | SCOUT | FUNGAL_J | ✓ | 22 | 23/22 | 2 | 5 |
| 223 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 16/22 | 5 | 8 |
| 224 | NIGH | SCOUT | FUNGAL_J | ✓ | 24 | 22/22 | 1 | 10 |
| 225 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 16/22 | 3 | 7 |
| 226 | NIGH | ENGINEER | STANDARD | ✓ | 24 | 22/22 | 4 | 21 |
| 227 | NIGH | ENGINEER | STANDARD | ✗ | 26 | 20/22 | 1 | 5 |
| 228 | NIGH | ENGINEER | STANDARD | ✓ | 21 | 23/22 | 1 | 4 |
| 229 | NIGH | ENGINEER | STANDARD | ✓ | 23 | 22/22 | 2 | 10 |
| 230 | NIGH | ENGINEER | STANDARD | ✓ | 20 | 22/22 | 1 | 1 |
| 231 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 23 | 22/22 | 1 | 3 |
| 232 | NIGH | ENGINEER | CRYSTAL_ | ✗ | 26 | 20/22 | 2 | 4 |
| 233 | NIGH | ENGINEER | CRYSTAL_ | ✗ | 26 | 20/22 | 0 | 7 |
| 234 | NIGH | ENGINEER | CRYSTAL_ | ✗ | 26 | 21/22 | 2 | 12 |
| 235 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 23 | 23/22 | 1 | 6 |
| 236 | NIGH | ENGINEER | FUNGAL_J | ✗ | 26 | 20/22 | 4 | 11 |
| 237 | NIGH | ENGINEER | FUNGAL_J | ✓ | 24 | 24/22 | 0 | 7 |
| 238 | NIGH | ENGINEER | FUNGAL_J | ✓ | 21 | 22/22 | 2 | 9 |
| 239 | NIGH | ENGINEER | FUNGAL_J | ✓ | 24 | 22/22 | 2 | 10 |
| 240 | NIGH | ENGINEER | FUNGAL_J | ✗ | 26 | 21/22 | 1 | 10 |
