import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';
import { DataLoaderService } from './core/services/data-loader.service';
import { DASHBOARD_API } from './core/services/dashboard-api.interface';
import { HttpDashboardApiService } from './core/services/http-dashboard-api.service';

/**
 * Preloads the static JSON asset so the local fallback services have data
 * available if needed. Not required when the HTTP adapter is the primary
 * provider, but kept so the local service remains functional without the backend.
 */
function initData(dataLoader: DataLoaderService) {
  return () => dataLoader.load();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(),
    {
      provide: APP_INITIALIZER,
      useFactory: initData,
      deps: [DataLoaderService],
      multi: true
    },
    {
      provide: DASHBOARD_API,
      useClass: HttpDashboardApiService
    }
  ]
};
