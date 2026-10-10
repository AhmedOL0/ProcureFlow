import { Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, Subscription } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Plus, Sparkles, Trash2 } from '../../shared/icons';
import { ApiFailure, parseApiFailure, userMessageFor } from '../../shared/utils/api-errors';
import { formatMinor } from '../../shared/utils/money';
import { ProcurementService } from '../procurement/procurement.service';
import { ChatAnswer, CopilotService, RequestDraft } from './copilot.service';

type CopilotMode = 'chat' | 'explain' | 'extract' | 'compare';

const MODES: { id: CopilotMode; label: string }[] = [
  { id: 'chat', label: 'Ask' },
  { id: 'explain', label: 'Explain spend' },
  { id: 'extract', label: 'Draft from words' },
  { id: 'compare', label: 'Compare quotes' },
];

/**
 * ProcureAI copilot: KPI-cited answers over workspace data, never generic
 * chat. Every answer renders its citations and model; failures (including
 * 503 AI_DISABLED while the backend flag is off) surface as messages, not
 * silence. Extracted drafts become real drafts only through the
 * request-creation contract — items stay listed for transcription because
 * supplier links need workspace validation the model cannot provide.
 */
@Component({
  selector: 'app-copilot',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatProgressSpinnerModule,
    LucideAngularModule,
    PageHeaderComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './copilot.component.html',
  styleUrl: './copilot.component.scss',
})
export class CopilotComponent {
  private readonly copilot = inject(CopilotService);
  private readonly requests = inject(ProcurementService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly icons = { plus: Plus, trash: Trash2, sparkles: Sparkles };
  protected readonly formatMinor = formatMinor;
  protected readonly modes = MODES;
  protected readonly canRequest = this.auth.hasAuthority('procurement:request');

  protected readonly mode = signal<CopilotMode>('chat');
  protected readonly busy = signal(false);
  protected readonly failure = signal<ApiFailure | null>(null);
  protected readonly answer = signal<ChatAnswer | null>(null);
  protected readonly draft = signal<RequestDraft | null>(null);
  protected readonly draftSaved = signal(false);
  private running: Subscription | null = null;

  readonly questionBox = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  readonly periodBox = new FormControl('', { nonNullable: true });
  readonly narrativeBox = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(12)],
  });
  readonly quoteRows = new FormArray([
    CopilotComponent.quoteGroup(),
    CopilotComponent.quoteGroup(),
  ]);

  private static quoteGroup(): FormGroup {
    return new FormGroup({
      supplier: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      amount: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)],
      }),
    });
  }

  setMode(mode: CopilotMode): void {
    if (this.busy()) {
      return;
    }
    this.mode.set(mode);
    this.failure.set(null);
  }

  /** Cancels the in-flight provider call (HttpClient aborts on unsubscribe). */
  cancel(): void {
    this.running?.unsubscribe();
    this.running = null;
    this.busy.set(false);
  }

  addQuote(): void {
    if (this.quoteRows.length < 10) {
      this.quoteRows.push(CopilotComponent.quoteGroup());
    }
  }

  removeQuote(index: number): void {
    if (this.quoteRows.length > 2) {
      this.quoteRows.removeAt(index);
    }
  }

  ask(): void {
    if (this.questionBox.invalid || this.busy()) {
      this.questionBox.markAsTouched();
      return;
    }
    this.run(this.copilot.chat(this.questionBox.value.trim()), (answer) => this.answer.set(answer));
  }

  explain(): void {
    if (this.busy()) {
      return;
    }
    const period = this.periodBox.value.trim() === '' ? null : this.periodBox.value.trim();
    this.run(this.copilot.explainSpend(period), (answer) => this.answer.set(answer));
  }

  extract(): void {
    if (this.narrativeBox.invalid || this.busy()) {
      this.narrativeBox.markAsTouched();
      return;
    }
    this.draft.set(null);
    this.draftSaved.set(false);
    this.run(this.copilot.extractRequest(this.narrativeBox.value.trim()), (draft) =>
      this.draft.set(draft as RequestDraft),
    );
  }

  compare(): void {
    if (this.quoteRows.invalid || this.busy()) {
      this.quoteRows.markAllAsTouched();
      return;
    }
    const quotes = this.quoteRows.getRawValue().map((row) => ({
      supplier: (row['supplier'] as string).trim(),
      amountMinor: Math.round(Number.parseFloat(row['amount'] as string) * 100),
    }));
    this.run(this.copilot.compareQuotations(quotes), (answer) => this.answer.set(answer));
  }

  saveDraft(): void {
    const draft = this.draft();
    if (!draft || this.busy()) {
      return;
    }
    // The model may return a priority outside the contract — fall back to
    // the server default instead of 400ing on its wording.
    const raw = draft.priority ?? '';
    const priority = (['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const).find((level) => level === raw);
    this.busy.set(true);
    this.failure.set(null);
    const idempotencyKey =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    this.requests
      .create(
        {
          title: (draft.title ?? 'Copilot draft').slice(0, 200),
          ...(priority ? { priority } : {}),
        },
        idempotencyKey,
      )
      .subscribe({
        next: (created) => {
          this.busy.set(false);
          this.draftSaved.set(true);
          void this.router.navigate(['/requests', created.id]);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.failure.set(parseApiFailure(error));
        },
      });
  }

  private run<T>(call: Observable<T>, apply: (result: T) => void): void {
    this.running?.unsubscribe();
    this.busy.set(true);
    this.failure.set(null);
    this.answer.set(null);
    this.running = call.subscribe({
      next: (result) => {
        this.running = null;
        this.busy.set(false);
        apply(result);
      },
      error: (error: unknown) => {
        this.running = null;
        this.busy.set(false);
        this.failure.set(parseApiFailure(error));
      },
    });
  }

  protected failureMessage(): string {
    const failure = this.failure();
    return failure ? userMessageFor(failure) : '';
  }
}
