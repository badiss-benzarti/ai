# AI design: Module 6 (Itinerary & budget)

## The two AI tasks

1. **Generate a personalised itinerary:** day-by-day activities, lunch and dinner, and one hotel, chosen for the traveller's interests and budget.
2. **Fit the budget automatically:** when the itinerary costs more than the budget, replace items with cheaper alternatives and explain each change.

Both run on a **local model**: Ollama with `qwen2.5:7b`, on the laptop's GPU (RTX 4060, 8 GB). It's free, works offline and keeps travel data private.

## Main principle: the AI chooses, Java decides

A 7-billion-parameter model is good at choosing and explaining, but unreliable at arithmetic and at following lists exactly.
So the work is split:

| The AI does | Java does |
|---|---|
| Choose places among ids we give it | Build the list of allowed ids, filtered by budget |
| Arrange them into days with times | Check every id exists and is not repeated |
| Give each day a title | Take every price from the catalog (never from the AI) |
| Explain why an item fits, or why a swap is a good idea | Compute all totals and compare them with the budget |
| Pick which items to swap for cheaper ones | Apply only valid swaps, stop when the budget fits |

The result is that **the AI can never invent a place or a price**, and the plan is always usable.

## Generation, step by step (`AiItineraryService`)

1. **Budget split in Java:** up to 45% of the budget for the hotel. The rest is shared between the days, half for meals and half for activities.
2. **Catalog filtering:** only hotels, activities and restaurants the budget can afford are sent to the model.
   We added this after testing: given the whole catalog, the model chose a 4-star hotel and a Michelin-starred restaurant for an 800 € trip.
3. **Prompt:** a system prompt with the rules (only use the given ids, lunch and dinner each day, no repeated activity, times in order, a note of at most
   12 words explaining why the item fits) and a user prompt with the trip, the limits per night and per day, and the filtered catalog.
4. **Structured output:** the request includes a **JSON schema** (Ollama's `format` parameter), so the model must answer with valid JSON of the expected shape.
5. **Validation:** Java drops unknown or repeated ids and unreadable times, adds a missing lunch or dinner, pushes back overlapping items,
   and fills any day the model skipped with the rule-based planner.
6. **Automatic budget check:** if the plan is still over budget, the optimizer below runs straight away.

## Budget optimization (`BudgetOptimizerService`)

1. For each item, Java lists up to 4 cheaper alternatives from the catalog (same category first, best rated first).
2. The model receives the budget, the amount to save, the interests and these alternatives, and returns the swaps with a reason for each.
3. Java applies the swaps one by one and **stops as soon as the total fits**. Without this rule, the model saved 778 € when 469 € was enough.
4. If the model's swaps are not enough (up to 3 rounds), a **greedy algorithm** finishes: the smallest single swap that closes the gap,
   or otherwise the largest saving. As a last resort, a paid activity is removed.

Example measured in the app: after switching to a 4-star hotel, the total was 1408 € for an 800 € budget. In 12 s the AI replaced only the hotel
(−660 €, reason: "Reduce hotel cost significantly") and stopped at 748 €.

## Reliability

| Risk | Protection |
|---|---|
| Ollama not running or model missing | Rule-based planner and greedy optimizer, and the UI shows "Rule-based plan · AI was unavailable" |
| Invalid JSON | JSON schema in the request, then parsing errors fall back to the rule-based plan |
| The model loops and never stops (seen in testing: 40,000+ tokens) | Answer capped at 3000 tokens, read timeout of 180 s |
| Slow first request (loading 5 GB into the GPU) | The model is loaded when the backend starts and kept for 30 minutes |
| Invented ids, repeated places, bad times, missing meals, overlaps | Java validation, covered by unit tests |

## Testing

The AI services are tested with a **fake Ollama** (Mockito), so the tests are fast and repeatable and don't need the model.
They feed the services bad answers on purpose: invented ids, unaffordable items, a repeated activity, unreadable times, missing days and meals,
and overlapping times. They also cover the optimizer's stopping rule, its greedy and last-resort paths, and the cost maths.
To check that the tests really detect bugs, we removed the optimizer's stopping rule on purpose: the test failed immediately.

Measured with the real model on the RTX 4060: an itinerary takes 13–35 s, and a budget optimization about 12 s.

## Limits

- A 7B model sometimes writes weak notes (repeating the place name), or places a restaurant at an odd time. The structure is always correct, only the wording varies.
- Results vary slightly from one run to the next (temperature 0.3).
- A bigger model would be better, but it would not fit in the 8 GB of the graphics card.
