import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { InMemoryCache } from '@apollo/client/cache';
import { SetContextLink } from '@apollo/client/link/context';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';

import { routes } from './app.routes';
import { FirebaseAuthService } from './core/auth/firebase-auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch()),
    provideApollo(() => {
      const auth = inject(FirebaseAuthService);
      const authorization = new SetContextLink(async (previousContext) => {
        const token = await auth.idToken();
        return {
          headers: {
            ...previousContext['headers'],
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        };
      });

      return {
        cache: new InMemoryCache(),
        link: authorization.concat(inject(HttpLink).create({ uri: '/graphql' })),
      };
    }),
    provideRouter(routes, withComponentInputBinding()),
  ],
};
