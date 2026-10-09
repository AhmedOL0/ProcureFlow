import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Post-login landing: who is signed in, into which workspace, with which
 * roles — plus sign-out. Feature modules (suppliers, requests, approvals…)
 * land here as lazy routes next.
 */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatButtonModule, MatChipsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  protected readonly auth = inject(AuthService);

  signOut(): void {
    this.auth.logout();
  }
}
