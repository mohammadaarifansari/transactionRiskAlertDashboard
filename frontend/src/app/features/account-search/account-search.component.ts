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
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Account } from '../../shared/models/account.model';
import { DataLoaderService } from '../../core/services/data-loader.service';

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

  private readonly dataLoader = inject(DataLoaderService);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly results = signal<Account[]>([]);
  protected readonly hasSearched = signal<boolean>(false);
  protected readonly isLoading = signal<boolean>(false);

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((query) => this.runSearch(query));
  }

  ngOnInit(): void {
    // intentionally empty – setup done in constructor via takeUntilDestroyed
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.runSearch(this.searchControl.value);
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

  private runSearch(query: string): void {
    const q = query.trim();
    if (!q) {
      this.results.set([]);
      this.hasSearched.set(false);
      return;
    }
    this.hasSearched.set(true);
    this.results.set(this.dataLoader.searchAccounts(q));
  }
}
