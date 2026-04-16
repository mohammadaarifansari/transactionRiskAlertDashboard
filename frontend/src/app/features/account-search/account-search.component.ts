import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  OnInit,
  Output,
  inject,
  signal
} from '@angular/core';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Account } from '../../shared/models/account.model';
import { DASHBOARD_API } from '../../core/services/dashboard-api.interface';

@Component({
  selector: 'app-account-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './account-search.component.html',
  styleUrl: './account-search.component.scss'
})
export class AccountSearchComponent implements OnInit {
  @Output() readonly accountSelected = new EventEmitter<Account>();
  @Output() readonly searchCleared = new EventEmitter<void>();

  private readonly api = inject(DASHBOARD_API);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly results = signal<Account[]>([]);
  protected readonly hasSearched = signal<boolean>(false);
  protected readonly isLoading = signal<boolean>(false);

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => {
          const q = query.trim();
          if (!q) {
            this.results.set([]);
            this.hasSearched.set(false);
            return of([]);
          }
          this.hasSearched.set(true);
          this.isLoading.set(true);
          return this.api.searchAccounts(q).pipe(catchError(() => of([])));
        }),
        takeUntilDestroyed()
      )
      .subscribe((accounts) => {
        this.results.set(accounts);
        this.isLoading.set(false);
      });
  }

  ngOnInit(): void {
    // intentionally empty – setup done in constructor via takeUntilDestroyed
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const q = this.searchControl.value.trim();
    if (!q) return;
    this.hasSearched.set(true);
    this.isLoading.set(true);
    this.api.searchAccounts(q).pipe(catchError(() => of([]))).subscribe((accounts) => {
      this.results.set(accounts);
      this.isLoading.set(false);
    });
  }

  protected selectAccount(account: Account): void {
    this.accountSelected.emit(account);
    this.results.set([]);
    this.hasSearched.set(false);
    this.searchControl.setValue(account.customerName, { emitEvent: false });
  }

  protected clearSearch(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.results.set([]);
    this.hasSearched.set(false);
    this.searchCleared.emit();
  }
}
