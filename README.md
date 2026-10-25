# TravelMate: Module 6 (Itinerary & budget)

Spring Boot 4.1 (Java 21) + Angular 21 + MySQL + Ollama.

More documents:
- [docs/INTEGRATION.md](docs/INTEGRATION.md): what Module 6 needs from the other modules (for the group)
- [docs/AI-DESIGN.md](docs/AI-DESIGN.md): how the AI tasks work and why (for the report)
- [docs/DEMO.md](docs/DEMO.md): demo script and checklist

## Run it

| What | Command | URL |
|---|---|---|
| MySQL | start it from XAMPP (root / no password, database `travelmate` is created automatically) | localhost:3306 |
| Ollama | `ollama pull qwen2.5:7b` once, then Ollama runs in the background | localhost:11434 |
| Backend | `cd backend` then `mvnw.cmd spring-boot:run` | http://localhost:8081 |
| Frontend | `cd frontend` then `npm install` (first time) and `npx ng serve` | http://localhost:4200 |

If Ollama is not running, the module still works: generation and budget optimization fall back to a rule-based algorithm (`generatedByAi` / `aiUsed` = false).
`GET /api/ai/status` tells whether the model is ready.

### Ollama setup (once)

1. Install it: `winget install Ollama.Ollama` (or the installer from ollama.com). Open a **new** terminal afterwards so `ollama` is on the PATH.
   If it still isn't found, use the full path: `%LOCALAPPDATA%\Programs\Ollama\ollama.exe`.
2. Download the model, about 4.7 GB (keep at least 8 GB free on the disk): `ollama pull qwen2.5:7b`
3. Check: `ollama list` shows `qwen2.5:7b`.

The backend loads the model when it starts and keeps it in memory for 30 minutes, so the first request is not slow.
With a GPU (tested on an RTX 4060), an itinerary takes 15–35 s and a budget optimization about 10 s.

### If a request gets stuck

A small model can sometimes loop on its JSON answer. `OllamaClient` caps the answer at 3000 tokens, so the request fails after
about a minute and the rule-based plan is used. If `ollama ps` still shows the model as busy (or "Stopping..."), Ollama is still
generating for an old request and every new one waits behind it. Restart it: quit Ollama from the system tray (or kill
`ollama.exe serve`) and start it again.

## Tests

`cd backend` then `mvnw.cmd test`. The AI tests use a fake Ollama, so they run in a few seconds without the model.
They check that the module copes with a bad AI answer (invented ids, repeated activities, unreadable times, missing days or meals,
overlapping times), that the optimizer stops as soon as the budget fits, and the cost maths.
`TravelmateApplicationTests` starts the whole app, so it needs MySQL running.

## AI tasks

1. **Generate the itinerary**: `AiItineraryService` sends the trip and the catalog (activities, restaurants, hotels with their ids and prices) to Ollama.
   Java first removes what the budget cannot afford and gives the model a limit per night and per day, because a 7B model is bad at adding prices.
   The model returns a JSON plan that can only reference those ids. Java validates every id, takes prices from the catalog, adds a missing lunch or
   dinner, fixes overlapping times and computes the totals. If the plan is still over budget, the optimizer below runs automatically.
2. **Fit the budget**: `BudgetOptimizerService` lists cheaper alternatives for each item. Ollama chooses the swaps and explains them, and Java applies
   only valid swaps, stopping as soon as the budget fits (up to 3 rounds). A greedy algorithm finishes the job if needed, and removing a paid
   activity is the last resort.

## API (`/api/itineraries`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/trips` | trips of the user (Module 2) |
| GET | `/` | itineraries of the user |
| POST | `/generate` `{tripId}` | AI generation (replaces the existing one) |
| GET / DELETE | `/{id}` | read / delete |
| GET | `/trip/{tripId}` | itinerary of a trip |
| GET | `/{id}/catalog?type=ACTIVITY` | catalog of the destination |
| POST | `/{id}/days/{dayId}/items` | add an item |
| PUT / DELETE | `/items/{itemId}` | edit / remove an item |
| POST | `/{id}/optimize` | AI budget optimization |
| GET | `/{id}/budget` | budget summary per category |
| GET / POST | `/{id}/expenses` | expenses |
| DELETE | `/expenses/{expenseId}` | delete an expense |
| GET | `/{id}/pdf` | PDF export |

## Integration with the other modules

Module 6 never reads another module's tables. Everything goes through 2 interfaces in
`backend/src/main/java/com/travelmate/itinerary/integration/`:

| Interface | Needs | From |
|---|---|---|
| `TripInfoProvider` | `getTrip(id)`, `getTripsForUser(userId)` | Module 2 (trips) |
| `CatalogProvider` | `getActivities / getRestaurants / getHotels(destination)` | Modules 3, 4, 5 |
| `CurrentUserProvider` | `currentUserId()` | Module 1 (login / JWT) |

Steps when merging:

1. Write `RealTripInfoProvider`, `RealCatalogProvider` and `JwtCurrentUserProvider` (annotated `@Component`) that call the other modules' services
   and convert their entities to `TripInfo` / `CatalogItem`.
2. Delete the `integration/mock` package and `resources/mock/mock-data.json`.
3. Nothing else changes: every endpoint already checks that the itinerary belongs to `currentUserId()`, and someone else's itinerary answers 404.
4. Frontend: copy `src/app/features/itinerary/` and add one line to the main `app.routes.ts`:
   ```ts
   { path: 'itinerary', loadChildren: () => import('./features/itinerary/itinerary.routes').then(m => m.ITINERARY_ROUTES) },
   ```
   Make sure `provideHttpClient()` is in `app.config.ts`. The `:root` colour variables are in `src/styles.css`.

**Shared with every module:** `com.travelmate.common.ai.OllamaClient`, which provides `chat(system, user)` for text and `chatJson(system, user, schema, Type.class)` for structured JSON.
Other modules can reuse it for their own AI tasks instead of writing another client.

## Conventions proposed to the group

- Java 21, Spring Boot 4.1.x, Angular 21, a single MySQL database `travelmate`
- Packages `com.travelmate.<module>`, REST prefix `/api/<module>`
- Backend on port **8081** (8080 is often taken by Oracle), Angular on 4200
