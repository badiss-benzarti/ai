import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BudgetService } from '../../services/budget.service';
import {
  BudgetSummary, Expense, ITEM_TYPES, ItemType, Itinerary, apiError,
} from '../../models/itinerary.models';

/** Budget tracking: planned cost vs real expenses, per category. */
@Component({
  selector: 'app-budget-dashboard',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './budget-dashboard.html',
  styleUrl: './budget-dashboard.css',
})
export class BudgetDashboard {
  private budgetService = inject(BudgetService);
  private fb = inject(FormBuilder);

  itinerary = input.required<Itinerary>();

  readonly categories = ITEM_TYPES;
  summary = signal<BudgetSummary | null>(null);
  expenses = signal<Expense[]>([]);
  error = signal<string | null>(null);

  /** Largest amount on the chart, used to scale the bars. */
  chartMax = computed(() => {
    const s = this.summary();
    if (!s) return 1;
    return Math.max(1, ...s.categories.flatMap(c => [c.estimated, c.spent]));
  });

  form = this.fb.group({
    category: this.fb.nonNullable.control<ItemType>('RESTAURANT', Validators.required),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    date: this.fb.control(new Date().toISOString().slice(0, 10)),
    description: this.fb.control(''),
  });

  constructor() {
    // Reload whenever the itinerary changes (item edited, optimization...)
    effect(() => this.reload(this.itinerary().id));
  }

  addExpense(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.budgetService.addExpense(this.itinerary().id, { ...v, amount: Number(v.amount) }).subscribe({
      next: () => {
        this.form.patchValue({ amount: null, description: '' });
        this.form.markAsUntouched();
        this.reload(this.itinerary().id);
      },
      error: err => this.error.set(apiError(err)),
    });
  }

  deleteExpense(e: Expense): void {
    this.budgetService.deleteExpense(e.id).subscribe({
      next: () => this.reload(this.itinerary().id),
      error: err => this.error.set(apiError(err)),
    });
  }

  percent(value: number): number {
    return (value / this.chartMax()) * 100;
  }

  private reload(id: number): void {
    this.budgetService.getSummary(id).subscribe({
      next: s => this.summary.set(s),
      error: err => this.error.set(apiError(err)),
    });
    this.budgetService.getExpenses(id).subscribe(list => this.expenses.set(list));
  }
}
