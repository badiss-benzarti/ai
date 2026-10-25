# What Module 6 (Itinerary & budget) needs from the other modules

Module 6 builds a day-by-day itinerary for a trip, estimates its cost and keeps it within budget.
It never reads your tables directly. It only calls the small Java interfaces below, so **you don't have to change your module**:
the Module 6 owner writes the adapter that converts your data. We just need to agree on the fields.

## Module 1: Users / login

| Module 6 needs | Type | Notes |
|---|---|---|
| id of the logged-in user | `Long` | read from the JWT on every request |

Questions:
1. Are you using Spring Security with a JWT? Where is the user id in the token (`sub` claim, custom claim)?
2. Does the user profile store **interests** (e.g. history, food, beach)? The AI uses them to personalise the itinerary.

## Module 2: Trips

| Module 6 needs | Type | Notes |
|---|---|---|
| trip id | `Long` | |
| owner's user id | `Long` | so a user only sees their own trips |
| destination | `String` | a city, e.g. `Rome`. Used to find activities, restaurants and hotels |
| start date, end date | `LocalDate` | both included: 10 → 14 November = 5 days, 4 hotel nights |
| total budget | `double`, EUR | |
| interests | `List<String>` | optional, can come from the user profile instead |

Module 6 calls: `getTrip(tripId)` and `getTripsForUser(userId)`.

## Modules 3, 4, 5: Activities, restaurants, hotels

Module 6 treats the three in the same way:

| Module 6 needs | Type | Activity | Restaurant | Hotel |
|---|---|---|---|---|
| id | `Long` | ✔ | ✔ | ✔ |
| name | `String` | ✔ | ✔ | ✔ |
| city | `String` | ✔ | ✔ | ✔ |
| category | `String` | e.g. `history`, `art`, `food` | e.g. `italian`, `street food` | e.g. `hostel`, `3*` |
| price | `double`, EUR | per person | average price of one meal | per night |
| duration | `Integer`, minutes | ✔ (optional) | optional | — |
| rating | `Double`, 0–5 | optional | optional | optional |

Module 6 calls: `getActivities(city)`, `getRestaurants(city)`, `getHotels(city)`.

Questions:
1. Is the city a plain text field, or a link to a `City` / `Destination` entity? The trip (Module 2) and the catalog must use the same city value.
2. Are prices in EUR? If not, which currency?

## Questions for the whole group

1. **One Spring Boot application or one per module?**
   With one application (simplest), Module 6 calls your `@Service` classes directly. With separate applications, it calls your REST endpoints instead.
2. **Database:** one shared MySQL database `travelmate`? Module 6 only creates its own tables: `itinerary`, `itinerary_day`, `itinerary_item`, `expense`.
3. **Conventions:** packages `com.travelmate.<module>`, REST prefix `/api/<module>`, backend on port 8081 (8080 is often taken by Oracle on Windows), Angular on 4200.

## What Module 6 offers to the others

- `com.travelmate.common.ai.OllamaClient`: a ready-to-use client for the local AI (Ollama, model `qwen2.5:7b`).
  `chat(system, user)` returns text and `chatJson(system, user, schema, MyDto.class)` returns structured JSON.
  It also checks whether the model is available and loads it in advance. Reuse it for your own AI features instead of writing another client.
- REST API under `/api/itineraries` (see the README), e.g. `GET /api/itineraries/trip/{tripId}` if the Trips page wants to link to the itinerary.
- Angular feature folder `src/app/features/itinerary/`, lazy-loaded on the `/itinerary` route.
