# TravelMate: Module 6 (Itinerary & budget)

Spring Boot 4.1 (Java 21) + Angular 21 + MySQL + Ollama.

## Run it

| What | Command | URL |
|---|---|---|
| MySQL | start it from XAMPP (root / no password, database `travelmate` is created automatically) | localhost:3306 |
| Ollama | `ollama pull qwen2.5:7b` once, then Ollama runs in the background | localhost:11434 |
| Backend | `cd backend` then `mvnw.cmd spring-boot:run` | http://localhost:8081 |
| Frontend | `cd frontend` then `npm install` (first time) and `npx ng serve` | http://localhost:4200 |

If Ollama is not running, the module still works: generation and budget optimization fall back to a rule-based algorithm (`generatedByAi` / `aiUsed` = false).

## AI tasks

1. **Generate the itinerary**: `AiItineraryService` sends the trip and the catalog (activities, restaurants, hotels with their ids and prices) to Ollama. The model returns a JSON plan that can only reference those ids. Java validates every id, takes prices from the catalog and computes the totals.
2. **Fit the budget**: `BudgetOptimizerService` lists cheaper alternatives for each item. Ollama chooses the swaps and explains them, and Java applies only valid swaps (up to 3 rounds). A greedy algorithm finishes the job if needed.

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

Steps when merging:

1. Write `RealTripInfoProvider` and `RealCatalogProvider` (annotated `@Component`) that call the other modules' services and convert their entities to `TripInfo` / `CatalogItem`.
2. Delete the `integration/mock` package and `resources/mock/mock-data.json`.
3. Replace the default `userId = 1` in `ItineraryController` with the logged-in user from Module 1 (JWT).
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
