import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('has no selected account by default', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app['selectedAccount']()).toBeNull();
  });

  it('sets selectedAccount when onAccountSelected is called', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const account = { accountId: 'ACC-001', customerName: 'Test User' };
    app['onAccountSelected'](account);
    expect(app['selectedAccount']()).toEqual(account);
  });

  it('clears selectedAccount when onSearchCleared is called', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app['onAccountSelected']({ accountId: 'ACC-001', customerName: 'Test User' });
    app['onSearchCleared']();
    expect(app['selectedAccount']()).toBeNull();
  });
});
