# Phase K-1 — Strategic Heuristic Playtest Report

**Total games:** 240
**Player model:** Utility-maximizing AI (enumerates legal actions, scores each by EV(VP) + structure synergy + risk-adjusted explore value, picks best).

## 1. Win Rates (skilled-player ceiling)

If a *good* player can't beat a difficulty, that's a balance flag. If they always win in a few turns, the difficulty isn't differentiated.

| Difficulty | Win % | Avg turns | Avg VP | VP target |
|---|---|---|---|---|
| Easy | 100% | 11.7 | 13.8 | 13 |
| Normal | 85% | 14.5 | 15.1 | 15 |
| Hard | 72% | 17.5 | 18.3 | 19 |
| Nightmare | 80% | 21.1 | 17.9 | 18 |

## 2. Decision Difficulty (does the game make you THINK?)

`gap` = top-1 utility minus top-2 utility. Small gap = the choice mattered. Big gap = one move dominates → boring.

| Difficulty | Avg gap | Median gap | High-stakes turns (gap<10) % | No-brainer turns (gap>60) % |
|---|---|---|---|---|
| Easy | 18.8 | 1.4 | 71% | 6% |
| Normal | 11.6 | 0.0 | 79% | 4% |
| Hard | 15.2 | 0.2 | 76% | 5% |
| Nightmare | 6.9 | 0.0 | 80% | 2% |

## 3. Replayability (do strategies vary?)

Average structure mix per (character × map). If a row is dominated by one structure type across ALL maps, the meta is solved.

| Character | Map | Avg lanterns | Avg outposts | Avg excavators | Avg specialists | Core Anchors |
|---|---|---|---|---|---|---|
| The Explorer | Standard | 3.3 | 1.2 | 0.5 | 3.3 | 0.00 |
| The Explorer | Crystal Caves | 3.0 | 1.5 | 0.2 | 1.9 | 0.00 |
| The Explorer | Fungal Jungle | 2.4 | 2.2 | 0.3 | 3.7 | 0.00 |
| The Prospector | Standard | 3.1 | 1.2 | 0.4 | 3.4 | 0.15 |
| The Prospector | Crystal Caves | 3.8 | 1.4 | 0.1 | 2.0 | 0.15 |
| The Prospector | Fungal Jungle | 2.9 | 2.2 | 0.2 | 4.1 | 0.00 |
| The Scout | Standard | 3.3 | 1.1 | 0.5 | 2.9 | 0.10 |
| The Scout | Crystal Caves | 2.7 | 1.9 | 0.1 | 1.7 | 0.05 |
| The Scout | Fungal Jungle | 2.5 | 2.5 | 0.1 | 3.5 | 0.00 |
| The Engineer | Standard | 4.2 | 0.9 | 0.4 | 3.4 | 0.00 |
| The Engineer | Crystal Caves | 4.3 | 0.9 | 0.2 | 2.7 | 0.05 |
| The Engineer | Fungal Jungle | 4.2 | 1.2 | 0.5 | 3.3 | 0.00 |

## 4. Dice Variance Impact

`dead roll %` = rolls that produced nothing. Catan-style games tolerate ~25% dead rolls. Higher than that and the game *feels random* even to a skilled player.

| Difficulty | Dead roll % | Avg plateau turns | Avg trades / game |
|---|---|---|---|
| Easy | 15% | 1.0 | 0.8 |
| Normal | 14% | 1.0 | 0.9 |
| Hard | 13% | 1.5 | 1.2 |
| Nightmare | 5% | 1.8 | 1.3 |

## 5. Action Variety

Average distinct action TYPES taken per game (BUILD-LANTERN, BUILD-OUTPOST, EXPLORE, TRADE, CLEAR_RUBBLE, etc).

| Difficulty | Avg distinct action types | Avg explores | Avg structures built | Avg abilities used |
|---|---|---|---|---|
| Easy | 5.7 | 4.3 | 7.7 | 7.9 |
| Normal | 5.5 | 6.6 | 7.8 | 10.2 |
| Hard | 6.3 | 7.1 | 9.3 | 12.3 |
| Nightmare | 5.0 | 11.7 | 7.4 | 0.4 |

## 6. Character × Map Win Rates

| Character | Standard | Crystal Caves | Fungal Jungle |
|---|---|---|---|
| The Explorer | 19/20 | 13/20 | 15/20 |
| The Prospector | 19/20 | 18/20 | 15/20 |
| The Scout | 18/20 | 18/20 | 9/20 |
| The Engineer | 20/20 | 20/20 | 18/20 |

## 7. Auto-detected Flags

- ✅ No automated red flags surfaced.

## 8. Per-Game Summary

| # | Diff | Char | Map | Won | Turns | VP | Plateau | Avg gap |
|---|---|---|---|---|---|---|---|---|
| 1 | EASY | EXPLORER | STANDARD | ✓ | 8 | 13/13 | 1 | 28 |
| 2 | EASY | EXPLORER | STANDARD | ✓ | 13 | 13/13 | 1 | 25 |
| 3 | EASY | EXPLORER | STANDARD | ✓ | 9 | 13/13 | 1 | 16 |
| 4 | EASY | EXPLORER | STANDARD | ✓ | 16 | 14/13 | 0 | 18 |
| 5 | EASY | EXPLORER | STANDARD | ✓ | 9 | 14/13 | 0 | 16 |
| 6 | EASY | EXPLORER | CRYSTAL_ | ✓ | 10 | 16/13 | 0 | 10 |
| 7 | EASY | EXPLORER | CRYSTAL_ | ✓ | 10 | 14/13 | 0 | 6 |
| 8 | EASY | EXPLORER | CRYSTAL_ | ✓ | 11 | 13/13 | 1 | 6 |
| 9 | EASY | EXPLORER | CRYSTAL_ | ✓ | 10 | 14/13 | 1 | 5 |
| 10 | EASY | EXPLORER | CRYSTAL_ | ✓ | 11 | 13/13 | 1 | 17 |
| 11 | EASY | EXPLORER | FUNGAL_J | ✓ | 11 | 15/13 | 1 | 26 |
| 12 | EASY | EXPLORER | FUNGAL_J | ✓ | 9 | 13/13 | 1 | 2 |
| 13 | EASY | EXPLORER | FUNGAL_J | ✓ | 13 | 13/13 | 2 | 10 |
| 14 | EASY | EXPLORER | FUNGAL_J | ✓ | 8 | 14/13 | 0 | 45 |
| 15 | EASY | EXPLORER | FUNGAL_J | ✓ | 12 | 15/13 | 1 | 12 |
| 16 | EASY | PROSPECT | STANDARD | ✓ | 10 | 17/13 | 1 | 90 |
| 17 | EASY | PROSPECT | STANDARD | ✓ | 11 | 13/13 | 1 | 37 |
| 18 | EASY | PROSPECT | STANDARD | ✓ | 13 | 13/13 | 2 | 5 |
| 19 | EASY | PROSPECT | STANDARD | ✓ | 13 | 15/13 | 2 | 4 |
| 20 | EASY | PROSPECT | STANDARD | ✓ | 13 | 14/13 | 1 | 19 |
| 21 | EASY | PROSPECT | CRYSTAL_ | ✓ | 11 | 13/13 | 0 | 4 |
| 22 | EASY | PROSPECT | CRYSTAL_ | ✓ | 13 | 14/13 | 1 | 21 |
| 23 | EASY | PROSPECT | CRYSTAL_ | ✓ | 12 | 14/13 | 1 | 26 |
| 24 | EASY | PROSPECT | CRYSTAL_ | ✓ | 11 | 14/13 | 0 | 5 |
| 25 | EASY | PROSPECT | CRYSTAL_ | ✓ | 10 | 13/13 | 0 | 2 |
| 26 | EASY | PROSPECT | FUNGAL_J | ✓ | 12 | 15/13 | 1 | 10 |
| 27 | EASY | PROSPECT | FUNGAL_J | ✓ | 14 | 13/13 | 0 | 14 |
| 28 | EASY | PROSPECT | FUNGAL_J | ✓ | 13 | 16/13 | 3 | 34 |
| 29 | EASY | PROSPECT | FUNGAL_J | ✓ | 15 | 14/13 | 2 | 10 |
| 30 | EASY | PROSPECT | FUNGAL_J | ✓ | 13 | 16/13 | 1 | 19 |
| 31 | EASY | SCOUT | STANDARD | ✓ | 12 | 14/13 | 0 | 38 |
| 32 | EASY | SCOUT | STANDARD | ✓ | 13 | 13/13 | 0 | 32 |
| 33 | EASY | SCOUT | STANDARD | ✓ | 11 | 13/13 | 2 | 27 |
| 34 | EASY | SCOUT | STANDARD | ✓ | 16 | 14/13 | 3 | 14 |
| 35 | EASY | SCOUT | STANDARD | ✓ | 15 | 14/13 | 2 | 33 |
| 36 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 14/13 | 1 | 6 |
| 37 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 13/13 | 0 | 11 |
| 38 | EASY | SCOUT | CRYSTAL_ | ✓ | 13 | 13/13 | 2 | 5 |
| 39 | EASY | SCOUT | CRYSTAL_ | ✓ | 16 | 14/13 | 1 | 15 |
| 40 | EASY | SCOUT | CRYSTAL_ | ✓ | 14 | 13/13 | 2 | 13 |
| 41 | EASY | SCOUT | FUNGAL_J | ✓ | 14 | 13/13 | 1 | 16 |
| 42 | EASY | SCOUT | FUNGAL_J | ✓ | 14 | 13/13 | 2 | 3 |
| 43 | EASY | SCOUT | FUNGAL_J | ✓ | 19 | 13/13 | 3 | 4 |
| 44 | EASY | SCOUT | FUNGAL_J | ✓ | 22 | 14/13 | 4 | 3 |
| 45 | EASY | SCOUT | FUNGAL_J | ✓ | 18 | 14/13 | 3 | 22 |
| 46 | EASY | ENGINEER | STANDARD | ✓ | 7 | 14/13 | 0 | 18 |
| 47 | EASY | ENGINEER | STANDARD | ✓ | 9 | 15/13 | 0 | 23 |
| 48 | EASY | ENGINEER | STANDARD | ✓ | 9 | 13/13 | 0 | 34 |
| 49 | EASY | ENGINEER | STANDARD | ✓ | 10 | 14/13 | 0 | 5 |
| 50 | EASY | ENGINEER | STANDARD | ✓ | 10 | 13/13 | 0 | 28 |
| 51 | EASY | ENGINEER | CRYSTAL_ | ✓ | 9 | 14/13 | 2 | 26 |
| 52 | EASY | ENGINEER | CRYSTAL_ | ✓ | 8 | 14/13 | 0 | 10 |
| 53 | EASY | ENGINEER | CRYSTAL_ | ✓ | 8 | 13/13 | 0 | 5 |
| 54 | EASY | ENGINEER | CRYSTAL_ | ✓ | 7 | 13/13 | 0 | 38 |
| 55 | EASY | ENGINEER | CRYSTAL_ | ✓ | 9 | 13/13 | 0 | 7 |
| 56 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 14/13 | 0 | 57 |
| 57 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 13/13 | 1 | 42 |
| 58 | EASY | ENGINEER | FUNGAL_J | ✓ | 11 | 14/13 | 0 | 34 |
| 59 | EASY | ENGINEER | FUNGAL_J | ✓ | 9 | 13/13 | 1 | 8 |
| 60 | EASY | ENGINEER | FUNGAL_J | ✓ | 8 | 13/13 | 0 | 9 |
| 61 | NORM | EXPLORER | STANDARD | ✓ | 14 | 17/15 | 0 | 10 |
| 62 | NORM | EXPLORER | STANDARD | ✓ | 14 | 16/15 | 2 | 10 |
| 63 | NORM | EXPLORER | STANDARD | ✓ | 11 | 15/15 | 1 | 8 |
| 64 | NORM | EXPLORER | STANDARD | ✓ | 13 | 15/15 | 2 | 14 |
| 65 | NORM | EXPLORER | STANDARD | ✓ | 16 | 18/15 | 1 | 22 |
| 66 | NORM | EXPLORER | CRYSTAL_ | ✗ | 19 | 14/15 | 1 | 8 |
| 67 | NORM | EXPLORER | CRYSTAL_ | ✓ | 14 | 15/15 | 1 | 6 |
| 68 | NORM | EXPLORER | CRYSTAL_ | ✓ | 15 | 16/15 | 0 | 13 |
| 69 | NORM | EXPLORER | CRYSTAL_ | ✗ | 19 | 14/15 | 2 | 6 |
| 70 | NORM | EXPLORER | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 14 |
| 71 | NORM | EXPLORER | FUNGAL_J | ✓ | 15 | 15/15 | 0 | 5 |
| 72 | NORM | EXPLORER | FUNGAL_J | ✓ | 16 | 15/15 | 3 | 11 |
| 73 | NORM | EXPLORER | FUNGAL_J | ✓ | 12 | 15/15 | 0 | 10 |
| 74 | NORM | EXPLORER | FUNGAL_J | ✓ | 14 | 16/15 | 2 | 4 |
| 75 | NORM | EXPLORER | FUNGAL_J | ✗ | 19 | 14/15 | 3 | 3 |
| 76 | NORM | PROSPECT | STANDARD | ✓ | 16 | 15/15 | 1 | 4 |
| 77 | NORM | PROSPECT | STANDARD | ✓ | 13 | 15/15 | 0 | 8 |
| 78 | NORM | PROSPECT | STANDARD | ✓ | 15 | 15/15 | 1 | 9 |
| 79 | NORM | PROSPECT | STANDARD | ✓ | 12 | 16/15 | 0 | 22 |
| 80 | NORM | PROSPECT | STANDARD | ✓ | 15 | 17/15 | 1 | 14 |
| 81 | NORM | PROSPECT | CRYSTAL_ | ✓ | 16 | 15/15 | 0 | 10 |
| 82 | NORM | PROSPECT | CRYSTAL_ | ✓ | 14 | 15/15 | 2 | 7 |
| 83 | NORM | PROSPECT | CRYSTAL_ | ✓ | 12 | 15/15 | 0 | 7 |
| 84 | NORM | PROSPECT | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 3 |
| 85 | NORM | PROSPECT | CRYSTAL_ | ✓ | 11 | 16/15 | 1 | 13 |
| 86 | NORM | PROSPECT | FUNGAL_J | ✓ | 15 | 17/15 | 1 | 10 |
| 87 | NORM | PROSPECT | FUNGAL_J | ✓ | 18 | 15/15 | 2 | 9 |
| 88 | NORM | PROSPECT | FUNGAL_J | ✓ | 14 | 15/15 | 0 | 22 |
| 89 | NORM | PROSPECT | FUNGAL_J | ✓ | 16 | 15/15 | 1 | 17 |
| 90 | NORM | PROSPECT | FUNGAL_J | ✓ | 11 | 15/15 | 1 | 29 |
| 91 | NORM | SCOUT | STANDARD | ✓ | 16 | 15/15 | 0 | 11 |
| 92 | NORM | SCOUT | STANDARD | ✓ | 12 | 15/15 | 0 | 23 |
| 93 | NORM | SCOUT | STANDARD | ✗ | 19 | 14/15 | 2 | 1 |
| 94 | NORM | SCOUT | STANDARD | ✓ | 14 | 15/15 | 1 | 27 |
| 95 | NORM | SCOUT | STANDARD | ✓ | 15 | 16/15 | 1 | 7 |
| 96 | NORM | SCOUT | CRYSTAL_ | ✓ | 15 | 15/15 | 2 | 5 |
| 97 | NORM | SCOUT | CRYSTAL_ | ✓ | 17 | 15/15 | 1 | 3 |
| 98 | NORM | SCOUT | CRYSTAL_ | ✓ | 16 | 16/15 | 0 | 7 |
| 99 | NORM | SCOUT | CRYSTAL_ | ✓ | 15 | 15/15 | 1 | 13 |
| 100 | NORM | SCOUT | CRYSTAL_ | ✓ | 11 | 15/15 | 0 | 3 |
| 101 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 13/15 | 2 | 10 |
| 102 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 13/15 | 2 | 7 |
| 103 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 14/15 | 1 | 6 |
| 104 | NORM | SCOUT | FUNGAL_J | ✓ | 18 | 15/15 | 1 | 13 |
| 105 | NORM | SCOUT | FUNGAL_J | ✗ | 19 | 11/15 | 7 | 5 |
| 106 | NORM | ENGINEER | STANDARD | ✓ | 12 | 15/15 | 0 | 12 |
| 107 | NORM | ENGINEER | STANDARD | ✓ | 10 | 15/15 | 0 | 25 |
| 108 | NORM | ENGINEER | STANDARD | ✓ | 11 | 16/15 | 1 | 15 |
| 109 | NORM | ENGINEER | STANDARD | ✓ | 15 | 15/15 | 1 | 6 |
| 110 | NORM | ENGINEER | STANDARD | ✓ | 12 | 15/15 | 0 | 23 |
| 111 | NORM | ENGINEER | CRYSTAL_ | ✓ | 12 | 15/15 | 1 | 1 |
| 112 | NORM | ENGINEER | CRYSTAL_ | ✓ | 11 | 16/15 | 0 | 12 |
| 113 | NORM | ENGINEER | CRYSTAL_ | ✓ | 10 | 15/15 | 0 | 28 |
| 114 | NORM | ENGINEER | CRYSTAL_ | ✓ | 13 | 15/15 | 0 | 26 |
| 115 | NORM | ENGINEER | CRYSTAL_ | ✓ | 10 | 15/15 | 0 | 24 |
| 116 | NORM | ENGINEER | FUNGAL_J | ✓ | 15 | 15/15 | 1 | 10 |
| 117 | NORM | ENGINEER | FUNGAL_J | ✓ | 11 | 16/15 | 0 | 22 |
| 118 | NORM | ENGINEER | FUNGAL_J | ✓ | 14 | 16/15 | 1 | 6 |
| 119 | NORM | ENGINEER | FUNGAL_J | ✗ | 19 | 13/15 | 4 | 7 |
| 120 | NORM | ENGINEER | FUNGAL_J | ✓ | 14 | 15/15 | 1 | 12 |
| 121 | HARD | EXPLORER | STANDARD | ✓ | 17 | 19/19 | 0 | 16 |
| 122 | HARD | EXPLORER | STANDARD | ✓ | 15 | 19/19 | 0 | 7 |
| 123 | HARD | EXPLORER | STANDARD | ✓ | 16 | 19/19 | 1 | 7 |
| 124 | HARD | EXPLORER | STANDARD | ✗ | 21 | 15/19 | 2 | 9 |
| 125 | HARD | EXPLORER | STANDARD | ✓ | 18 | 20/19 | 2 | 42 |
| 126 | HARD | EXPLORER | CRYSTAL_ | ✓ | 12 | 19/19 | 0 | 18 |
| 127 | HARD | EXPLORER | CRYSTAL_ | ✗ | 21 | 17/19 | 4 | 7 |
| 128 | HARD | EXPLORER | CRYSTAL_ | ✗ | 21 | 15/19 | 0 | 3 |
| 129 | HARD | EXPLORER | CRYSTAL_ | ✗ | 21 | 18/19 | 7 | 4 |
| 130 | HARD | EXPLORER | CRYSTAL_ | ✓ | 18 | 19/19 | 0 | 7 |
| 131 | HARD | EXPLORER | FUNGAL_J | ✓ | 20 | 19/19 | 2 | 5 |
| 132 | HARD | EXPLORER | FUNGAL_J | ✗ | 21 | 13/19 | 1 | 1 |
| 133 | HARD | EXPLORER | FUNGAL_J | ✗ | 21 | 18/19 | 1 | 11 |
| 134 | HARD | EXPLORER | FUNGAL_J | ✓ | 20 | 19/19 | 2 | 12 |
| 135 | HARD | EXPLORER | FUNGAL_J | ✓ | 19 | 20/19 | 2 | 16 |
| 136 | HARD | PROSPECT | STANDARD | ✗ | 21 | 18/19 | 5 | 16 |
| 137 | HARD | PROSPECT | STANDARD | ✓ | 16 | 19/19 | 1 | 4 |
| 138 | HARD | PROSPECT | STANDARD | ✓ | 13 | 19/19 | 1 | 33 |
| 139 | HARD | PROSPECT | STANDARD | ✓ | 13 | 20/19 | 1 | 7 |
| 140 | HARD | PROSPECT | STANDARD | ✓ | 16 | 19/19 | 1 | 34 |
| 141 | HARD | PROSPECT | CRYSTAL_ | ✓ | 16 | 19/19 | 0 | 6 |
| 142 | HARD | PROSPECT | CRYSTAL_ | ✓ | 18 | 19/19 | 1 | 16 |
| 143 | HARD | PROSPECT | CRYSTAL_ | ✓ | 10 | 19/19 | 0 | 2 |
| 144 | HARD | PROSPECT | CRYSTAL_ | ✓ | 18 | 19/19 | 1 | 8 |
| 145 | HARD | PROSPECT | CRYSTAL_ | ✓ | 16 | 19/19 | 1 | 12 |
| 146 | HARD | PROSPECT | FUNGAL_J | ✗ | 21 | 14/19 | 3 | 10 |
| 147 | HARD | PROSPECT | FUNGAL_J | ✓ | 18 | 19/19 | 0 | 10 |
| 148 | HARD | PROSPECT | FUNGAL_J | ✗ | 21 | 15/19 | 4 | 19 |
| 149 | HARD | PROSPECT | FUNGAL_J | ✓ | 15 | 20/19 | 1 | 10 |
| 150 | HARD | PROSPECT | FUNGAL_J | ✗ | 21 | 15/19 | 4 | 7 |
| 151 | HARD | SCOUT | STANDARD | ✓ | 16 | 19/19 | 1 | 17 |
| 152 | HARD | SCOUT | STANDARD | ✓ | 20 | 20/19 | 7 | 6 |
| 153 | HARD | SCOUT | STANDARD | ✗ | 21 | 17/19 | 3 | 8 |
| 154 | HARD | SCOUT | STANDARD | ✓ | 13 | 19/19 | 0 | 17 |
| 155 | HARD | SCOUT | STANDARD | ✓ | 14 | 19/19 | 0 | 21 |
| 156 | HARD | SCOUT | CRYSTAL_ | ✗ | 21 | 14/19 | 2 | 6 |
| 157 | HARD | SCOUT | CRYSTAL_ | ✓ | 17 | 19/19 | 1 | 11 |
| 158 | HARD | SCOUT | CRYSTAL_ | ✗ | 21 | 13/19 | 3 | 8 |
| 159 | HARD | SCOUT | CRYSTAL_ | ✓ | 18 | 19/19 | 0 | 14 |
| 160 | HARD | SCOUT | CRYSTAL_ | ✓ | 19 | 19/19 | 1 | 8 |
| 161 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 18/19 | 3 | 14 |
| 162 | HARD | SCOUT | FUNGAL_J | ✓ | 19 | 19/19 | 1 | 4 |
| 163 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 13/19 | 6 | 7 |
| 164 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 13/19 | 5 | 16 |
| 165 | HARD | SCOUT | FUNGAL_J | ✗ | 21 | 16/19 | 2 | 15 |
| 166 | HARD | ENGINEER | STANDARD | ✓ | 16 | 19/19 | 1 | 19 |
| 167 | HARD | ENGINEER | STANDARD | ✓ | 13 | 20/19 | 0 | 50 |
| 168 | HARD | ENGINEER | STANDARD | ✓ | 17 | 20/19 | 1 | 13 |
| 169 | HARD | ENGINEER | STANDARD | ✓ | 15 | 20/19 | 0 | 27 |
| 170 | HARD | ENGINEER | STANDARD | ✓ | 18 | 19/19 | 1 | 18 |
| 171 | HARD | ENGINEER | CRYSTAL_ | ✓ | 14 | 19/19 | 0 | 25 |
| 172 | HARD | ENGINEER | CRYSTAL_ | ✓ | 15 | 21/19 | 1 | 84 |
| 173 | HARD | ENGINEER | CRYSTAL_ | ✓ | 13 | 20/19 | 0 | 26 |
| 174 | HARD | ENGINEER | CRYSTAL_ | ✓ | 15 | 20/19 | 0 | 12 |
| 175 | HARD | ENGINEER | CRYSTAL_ | ✓ | 13 | 20/19 | 0 | 20 |
| 176 | HARD | ENGINEER | FUNGAL_J | ✓ | 13 | 21/19 | 0 | 20 |
| 177 | HARD | ENGINEER | FUNGAL_J | ✓ | 17 | 19/19 | 0 | 18 |
| 178 | HARD | ENGINEER | FUNGAL_J | ✓ | 20 | 19/19 | 1 | 16 |
| 179 | HARD | ENGINEER | FUNGAL_J | ✓ | 15 | 19/19 | 1 | 11 |
| 180 | HARD | ENGINEER | FUNGAL_J | ✓ | 16 | 20/19 | 0 | 21 |
| 181 | NIGH | EXPLORER | STANDARD | ✓ | 22 | 19/18 | 1 | 8 |
| 182 | NIGH | EXPLORER | STANDARD | ✓ | 20 | 19/18 | 2 | 4 |
| 183 | NIGH | EXPLORER | STANDARD | ✓ | 22 | 18/18 | 1 | 3 |
| 184 | NIGH | EXPLORER | STANDARD | ✓ | 19 | 18/18 | 0 | 5 |
| 185 | NIGH | EXPLORER | STANDARD | ✓ | 19 | 18/18 | 2 | 11 |
| 186 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 18 | 18/18 | 2 | 10 |
| 187 | NIGH | EXPLORER | CRYSTAL_ | ✗ | 26 | 17/18 | 1 | 6 |
| 188 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 18 | 20/18 | 1 | 3 |
| 189 | NIGH | EXPLORER | CRYSTAL_ | ✓ | 13 | 20/18 | 0 | 5 |
| 190 | NIGH | EXPLORER | CRYSTAL_ | ✗ | 26 | 16/18 | 1 | 1 |
| 191 | NIGH | EXPLORER | FUNGAL_J | ✓ | 22 | 19/18 | 1 | 4 |
| 192 | NIGH | EXPLORER | FUNGAL_J | ✗ | 26 | 13/18 | 4 | 8 |
| 193 | NIGH | EXPLORER | FUNGAL_J | ✗ | 26 | 15/18 | 4 | 4 |
| 194 | NIGH | EXPLORER | FUNGAL_J | ✓ | 23 | 19/18 | 1 | 6 |
| 195 | NIGH | EXPLORER | FUNGAL_J | ✓ | 23 | 19/18 | 0 | 7 |
| 196 | NIGH | PROSPECT | STANDARD | ✓ | 22 | 18/18 | 2 | 3 |
| 197 | NIGH | PROSPECT | STANDARD | ✓ | 20 | 18/18 | 1 | 11 |
| 198 | NIGH | PROSPECT | STANDARD | ✓ | 16 | 18/18 | 0 | 4 |
| 199 | NIGH | PROSPECT | STANDARD | ✓ | 15 | 19/18 | 0 | 4 |
| 200 | NIGH | PROSPECT | STANDARD | ✓ | 18 | 18/18 | 3 | 7 |
| 201 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 22 | 18/18 | 1 | 1 |
| 202 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 21 | 18/18 | 2 | 6 |
| 203 | NIGH | PROSPECT | CRYSTAL_ | ✓ | 19 | 18/18 | 2 | 6 |
| 204 | NIGH | PROSPECT | CRYSTAL_ | ✗ | 26 | 15/18 | 2 | 8 |
| 205 | NIGH | PROSPECT | CRYSTAL_ | ✗ | 26 | 17/18 | 2 | 3 |
| 206 | NIGH | PROSPECT | FUNGAL_J | ✓ | 23 | 18/18 | 1 | 4 |
| 207 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 15/18 | 9 | 7 |
| 208 | NIGH | PROSPECT | FUNGAL_J | ✓ | 24 | 19/18 | 5 | 12 |
| 209 | NIGH | PROSPECT | FUNGAL_J | ✗ | 26 | 17/18 | 2 | 3 |
| 210 | NIGH | PROSPECT | FUNGAL_J | ✓ | 21 | 21/18 | 3 | 1 |
| 211 | NIGH | SCOUT | STANDARD | ✓ | 22 | 19/18 | 4 | 11 |
| 212 | NIGH | SCOUT | STANDARD | ✓ | 20 | 18/18 | 2 | 5 |
| 213 | NIGH | SCOUT | STANDARD | ✓ | 15 | 18/18 | 1 | 2 |
| 214 | NIGH | SCOUT | STANDARD | ✓ | 21 | 20/18 | 2 | 9 |
| 215 | NIGH | SCOUT | STANDARD | ✓ | 23 | 18/18 | 1 | 11 |
| 216 | NIGH | SCOUT | CRYSTAL_ | ✓ | 17 | 19/18 | 2 | 6 |
| 217 | NIGH | SCOUT | CRYSTAL_ | ✓ | 19 | 18/18 | 2 | 5 |
| 218 | NIGH | SCOUT | CRYSTAL_ | ✓ | 23 | 18/18 | 1 | 5 |
| 219 | NIGH | SCOUT | CRYSTAL_ | ✓ | 16 | 18/18 | 0 | 4 |
| 220 | NIGH | SCOUT | CRYSTAL_ | ✓ | 15 | 18/18 | 0 | 2 |
| 221 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 15/18 | 6 | 6 |
| 222 | NIGH | SCOUT | FUNGAL_J | ✓ | 24 | 18/18 | 1 | 9 |
| 223 | NIGH | SCOUT | FUNGAL_J | ✓ | 23 | 19/18 | 4 | 14 |
| 224 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 17/18 | 3 | 9 |
| 225 | NIGH | SCOUT | FUNGAL_J | ✗ | 26 | 17/18 | 7 | 6 |
| 226 | NIGH | ENGINEER | STANDARD | ✓ | 22 | 19/18 | 3 | 17 |
| 227 | NIGH | ENGINEER | STANDARD | ✓ | 20 | 18/18 | 3 | 5 |
| 228 | NIGH | ENGINEER | STANDARD | ✓ | 16 | 18/18 | 0 | 11 |
| 229 | NIGH | ENGINEER | STANDARD | ✓ | 16 | 18/18 | 0 | 16 |
| 230 | NIGH | ENGINEER | STANDARD | ✓ | 25 | 18/18 | 2 | 9 |
| 231 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 23 | 18/18 | 0 | 5 |
| 232 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 21 | 18/18 | 1 | 2 |
| 233 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 18 | 18/18 | 0 | 15 |
| 234 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 18 | 19/18 | 0 | 4 |
| 235 | NIGH | ENGINEER | CRYSTAL_ | ✓ | 16 | 18/18 | 1 | 6 |
| 236 | NIGH | ENGINEER | FUNGAL_J | ✓ | 20 | 18/18 | 0 | 13 |
| 237 | NIGH | ENGINEER | FUNGAL_J | ✓ | 20 | 18/18 | 1 | 18 |
| 238 | NIGH | ENGINEER | FUNGAL_J | ✓ | 22 | 19/18 | 1 | 7 |
| 239 | NIGH | ENGINEER | FUNGAL_J | ✗ | 26 | 16/18 | 5 | 13 |
| 240 | NIGH | ENGINEER | FUNGAL_J | ✓ | 19 | 18/18 | 0 | 6 |
