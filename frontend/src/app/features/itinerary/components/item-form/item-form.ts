import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ItineraryService } from '../../services/itinerary.service';
import {
  CATALOG_TYPES, CatalogItem, ITEM_TYPES, ItemRequest, ItemType, Itinerary, ItineraryDay, ItineraryItem, apiError,
} from '../../models/itinerary.models';

/** Modal to add an item to a day or edit an existing one. */
@Component({
  selector: 'app-item-form',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './item-form.html',
  styleUrl: './item-form.css',
})
export class ItemForm implements OnInit {
  private fb = inject(FormBuilder);
  private service = inject(ItineraryService);
  private destroyRef = inject(DestroyRef);

  itineraryId = input.required<number>();
  day = input.required<ItineraryDay>();
  item = input<ItineraryItem | null>(null);

  saved = output<Itinerary>();
  cancelled = output<void>();

  readonly types = ITEM_TYPES;
  catalog = signal<CatalogItem[]>([]);
  saving = signal(false);
  error = signal<string | null>(null);

  form = this.fb.group({
    type: this.fb.nonNullable.control<ItemType>('ACTIVITY', Validators.required),
    refId: this.fb.control<number | null>(null),
    name: this.fb.control('', Validators.required),
    category: this.fb.control(''),
    startTime: this.fb.control(''),
    endTime: this.fb.control(''),
    unitPrice: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    quantity: this.fb.control<number | null>(1, [Validators.required, Validators.min(1)]),
    notes: this.fb.control(''),
  });

  ngOnInit(): void {
    const item = this.item();
    if (item) {
      this.form.patchValue({
        ...item,
        startTime: item.startTime?.slice(0, 5) ?? '',
        endTime: item.endTime?.slice(0, 5) ?? '',
      });
    }
    this.loadCatalog(this.form.controls.type.value);

    this.form.controls.type.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(type => {
      this.form.controls.refId.setValue(null);
      this.loadCatalog(type);
    });
    this.form.controls.refId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refId => {
      const c = this.catalog().find(x => x.id === Number(refId));
      if (c) this.form.patchValue({ name: c.name, category: c.category, unitPrice: c.price });
    });
  }

  isCatalogType(): boolean {
    return CATALOG_TYPES.includes(this.form.controls.type.value);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: ItemRequest = {
      ...v,
      refId: v.refId ? Number(v.refId) : null,
      startTime: v.startTime || null,
      endTime: v.endTime || null,
    };
    const item = this.item();
    const call = item
      ? this.service.updateItem(item.id, request)
      : this.service.addItem(this.itineraryId(), this.day().id, request);

    this.saving.set(true);
    call.subscribe({
      next: it => this.saved.emit(it),
      error: err => {
        this.error.set(apiError(err));
        this.saving.set(false);
      },
    });
  }

  private loadCatalog(type: ItemType): void {
    this.catalog.set([]);
    if (!CATALOG_TYPES.includes(type)) return;
    this.service.getCatalog(this.itineraryId(), type).subscribe(list => this.catalog.set(list));
  }
}
