import {ApplicationConfig, provideZonelessChangeDetection} from '@angular/core';
import {provideRouter} from '@angular/router';
import {routes} from './app.routes';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {provideMarkdown} from 'ngx-markdown';
import {HttpRequest, HttpHandlerFn} from '@angular/common/http';

// Interceptor to add credentials to all requests
function credentialsInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn) {
  // Clone the request with credentials set to 'include' to send cookies
  const credsReq = req.clone({
    setHeaders: {},
    withCredentials: true
  });
  return next(credsReq);
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([credentialsInterceptor])),
    provideMarkdown()
  ]
};
