import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ItineraryService } from '../../services/itinerary.service';
import { BudgetService } from '../../services/budget.service';
import {
  Itinerary, ItineraryDay, ItineraryItem, OptimizationResult, apiError,
} from '../../models/itinerary.models';
import { ItemForm } from '../item-form/item-form';
import { BudgetDashboard } from '../budget-dashboard/budget-dashboard';
import { elapsedTimer } from '../../utils/elapsed-timer';

/** Day-by-day itinerary with AI budget optimization and PDF export. */
@Component({
  selector: 'app-itinerary-view',
  imports: [RouterLink, DatePipe, CurrencyPipe, DecimalPipe, ItemForm, BudgetDashboard],
  templateUrl: './itinerary-view.html',
  styleUrl: './itinerary-view.css',
})
export class ItineraryView implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(ItineraryService);
  private budgetService = inject(BudgetService);

  itinerary = signal<Itinerary | null>(null);
  tab = signal<'plan' | 'budget'>('plan');
  error = signal<string | null>(null);
  busy = signal<'optimize' | 'regenerate' | 'pdf' | null>(null);
  optimization = signal<OptimizationResult | null>(null);
  /** Item form state: day to add to, and item when editing. */
  form = signal<{ day: ItineraryDay; item: ItineraryItem | null } | null>(null);
  timer = elapsedTimer();

  budgetUsage = computed(() => {
    const it = this.itinerary();
    return it && it.budget > 0 ? (it.totalEstimatedCost / it.budget) * 100 : 0;
  });

  ngOnInit(): void {
    // Set by the home page when the generation fixed the budget automatically
    this.optimization.set(history.state?.optimization ?? null);
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.service.getById(id).subscribe({
      next: it => this.itinerary.set(it),
      error: err => this.error.set(apiError(err)),
    });
  }

  optimize(): void {
    const it = this.itinerary()!;
    this.busy.set('optimize');
    this.error.set(null);
    this.timer.start();
    this.service.optimize(it.id).subscribe({
      next: result => {
        this.optimization.set(result);
        this.itinerary.set(result.itinerary);
        this.busy.set(null);
        this.timer.stop();
      },
      error: err => this.fail(err),
    });
  }

  regenerate(): void {
    if (!confirm('Regenerate the whole itinerary? Your manual changes will be lost.')) return;
    this.busy.set('regenerate');
    this.error.set(null);
    this.optimization.set(null);
    this.timer.start();
    this.service.generate(this.itinerary()!.tripId).subscribe({
      next: result => {
        this.busy.set(null);
        this.timer.stop();
        this.itinerary.set(result.itinerary);
        this.optimization.set(result.optimization);
        this.router.navigate(['/itinerary', result.itinerary.id], { replaceUrl: true });
      },
      error: err => this.fail(err),
    });
  }

  downloadPdf(): void {
    const it = this.itinerary()!;
    this.busy.set('pdf');
    this.budgetService.downloadPdf(it.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `itinerary-${it.destination.toLowerCase()}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.busy.set(null);
      },
      error: err => this.fail(err),
    });
  }

  deleteItinerary(): void {
    if (!confirm('Delete this itinerary?')) return;
    this.service.delete(this.itinerary()!.id).subscribe({
      next: () => this.router.navigate(['/itinerary']),
      error: err => this.fail(err),
    });
  }

  deleteItem(item: ItineraryItem): void {
    if (!confirm(`Remove "${item.name}"?`)) return;
    this.service.deleteItem(item.id).subscribe({
      next: it => this.itinerary.set(it),
      error: err => this.fail(err),
    });
  }

  onItemSaved(it: Itinerary): void {
    this.itinerary.set(it);
    this.form.set(null);
  }

  time(t: string | null): string {
    return t ? t.slice(0, 5) : '';
  }

  private fail(err: unknown): void {
    this.error.set(apiError(err));
    this.busy.set(null);
    this.timer.stop();
  }
}
