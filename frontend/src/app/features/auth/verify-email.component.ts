import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Mailbox verification landing: reads ?token= from the emailed link and
 * redeems it. Unknown tokens 404, spent or expired ones 410 — each with
 * words a non-technical user can act on (ask for a fresh link).
 */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './verify-email.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class VerifyEmailComponent {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly state = signal<'working' | 'done' | 'invalid' | 'spent' | 'missing'>('working');

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const token = params.get('token')?.trim();
      if (!token) {
        this.state.set('missing');
        return;
      }
      this.auth.verifyEmail(token).subscribe({
        next: () => this.state.set('done'),
        error: (error: unknown) => {
          const code = this.auth.apiErrorCode(error);
          this.state.set(code === 'TOKEN_SPENT' ? 'spent' : 'invalid');
        },
      });
    });
  }
}
