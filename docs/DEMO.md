# Demo script: Module 6 (about 5 minutes)

## Before the demo (10 minutes earlier)

1. At least 8 GB free on the disk.
2. Start **MySQL** from XAMPP.
3. Check **Ollama** is running (llama icon in the system tray), then in a terminal: `ollama list` shows `qwen2.5:7b`.
4. Start the backend: `cd backend` then `mvnw.cmd spring-boot:run`. Wait for `Ollama model qwen2.5:7b loaded` in the log:
   the model is now in GPU memory and stays there for 30 minutes.
5. Start the frontend: `cd frontend` then `npx ng serve`, and open http://localhost:4200/itinerary.
6. Check the badge at the top shows **AI ready · qwen2.5:7b**.
7. Do one full run-through of the steps below, so nothing is slow the first time.

## The demo

| # | Do | Say |
|---|---|---|
| 1 | Show the 3 trips (Rome, Paris, Tunis) | "The trips come from Module 2. Each one has dates, a budget and the traveller's interests." |
| 2 | Click **Generate with AI** on Rome | "A local AI, Ollama with Qwen 2.5 7B on my graphics card, builds the plan. Nothing leaves the laptop." Point at the seconds counter. |
| 3 | Open the itinerary and scroll the days | "Every place comes from our catalog. The AI only chooses ids, and Java checks each one and computes every price. Each item has a short note saying why it fits the traveller." |
| 4 | Point at the budget bar | "Estimated cost against the budget, live." |
| 5 | Edit the hotel (✎) and pick **Hotel Artemide (4\*)** | "Now the traveller wants a luxury hotel and goes over budget." The bar turns red. |
| 6 | Click **✨ Fit my budget** | "The second AI task: it looks for cheaper alternatives and explains each swap. It stops as soon as the budget fits, so it doesn't spoil the whole trip." Read one reason aloud. |
| 7 | **Budget tracking** tab: add an expense (Restaurant, 25 €) | "During the trip, real spending is tracked against the estimate, per category." |
| 8 | Click **Export PDF** and open it | "The final itinerary to take on the trip." |

## If something goes wrong

- **The AI is slow or Ollama is off:** the plan is still produced by the rule-based fallback and labelled "Rule-based plan". Say: "This is the safety net. The module never depends on the AI to work."
- **A request seems stuck for more than a minute:** `ollama ps` shows the model as busy or "Stopping...". Quit Ollama from the system tray, start it again, and restart the backend.
- **The page is empty:** the backend is not running, or MySQL is not started.

## Likely questions

- **Why a local model and not ChatGPT?** It's free, works offline, and travel data stays private. The shared `OllamaClient` lets us switch the model in one setting.
- **What if the AI invents a place?** Impossible in the final plan: Java drops every id that is not in the catalog. This is covered by unit tests with a fake AI.
- **How do you know it stays within budget?** Java does all the maths, never the AI. If the plan is over budget, the optimizer runs automatically. If the AI's swaps are not enough, a greedy algorithm and, as a last resort, removing a paid activity bring it within budget. If the budget is simply too small (less than the cheapest hotel), the app says so.
