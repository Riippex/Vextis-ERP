import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { InMemoryCache } from '@apollo/client/cache';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch()),
    provideApollo(() => ({
      cache: new InMemoryCache(),
      link: inject(HttpLink).create({ uri: '/graphql' }),
    })),
    provideRouter(routes, withComponentInputBinding()),
  ],
};
