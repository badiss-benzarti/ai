export type ItemType = 'ACTIVITY' | 'RESTAURANT' | 'HOTEL' | 'TRANSPORT' | 'CUSTOM';

export const ITEM_TYPES: ItemType[] = ['ACTIVITY', 'RESTAURANT', 'HOTEL', 'TRANSPORT', 'CUSTOM'];
export const CATALOG_TYPES: ItemType[] = ['ACTIVITY', 'RESTAURANT', 'HOTEL'];

/** Trip coming from Module 2. */
export interface TripInfo {
  id: number;
  userId: number;
  destination: string;
  startDate: string;
  endDate: string;
  budget: number;
  interests: string[];
}

/** Activity (Module 3), restaurant (Module 4) or hotel (Module 5). */
export interface CatalogItem {
  id: number;
  type: ItemType;
  name: string;
  category: string;
  price: number;
  durationMinutes: number | null;
  rating: number | null;
}

export interface ItineraryItem {
  id: number;
  type: ItemType;
  refId: number | null;
  name: string;
  category: string | null;
  startTime: string | null;
  endTime: string | null;
  unitPrice: number;
  quantity: number;
  estimatedCost: number;
  notes: string | null;
}

export interface ItineraryDay {
  id: number;
  dayNumber: number;
  date: string;
  title: string;
  dayCost: number;
  items: ItineraryItem[];
}

export interface Itinerary {
  id: number;
  tripId: number;
  userId: number;
  destination: string;
  startDate: string;
  endDate: string;
  budget: number;
  totalEstimatedCost: number;
  overBudget: boolean;
  generatedByAi: boolean;
  createdAt: string;
  days: ItineraryDay[];
}

export interface ItemRequest {
  type: ItemType;
  refId?: number | null;
  name?: string | null;
  category?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  unitPrice?: number | null;
  quantity?: number | null;
  notes?: string | null;
}

export interface Replacement {
  itemId: number;
  dayNumber: number;
  type: ItemType;
  oldName: string;
  newName: string;
  oldCost: number;
  newCost: number;
  reason: string;
}

export interface OptimizationResult {
  itinerary: Itinerary;
  budget: number;
  totalBefore: number;
  totalAfter: number;
  withinBudget: boolean;
  aiUsed: boolean;
  replacements: Replacement[];
  message: string;
}

/** optimization is set when the generated itinerary was over budget and got fixed automatically. */
export interface GenerationResult {
  itinerary: Itinerary;
  optimization: OptimizationResult | null;
}

export interface AiStatus {
  available: boolean;
  model: string;
}

export interface CategoryBudget {
  category: ItemType;
  estimated: number;
  spent: number;
}

export interface BudgetSummary {
  budget: number;
  estimatedTotal: number;
  spentTotal: number;
  remainingEstimated: number;
  remainingActual: number;
  overBudget: boolean;
  categories: CategoryBudget[];
}

export interface Expense {
  id: number;
  category: ItemType;
  amount: number;
  date: string;
  description: string | null;
}

export interface ExpenseRequest {
  category: ItemType;
  amount: number;
  date?: string | null;
  description?: string | null;
}

/** Reads the { error } body sent by the backend. */
export function apiError(err: any): string {
  return err?.error?.error ?? 'Server unreachable. Is the backend running on port 8081?';
}
