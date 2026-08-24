import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { FirebaseAuthService } from './firebase-auth.service';

export const authenticatedGuard: CanActivateFn = async () => {
  const auth = inject(FirebaseAuthService);
  const router = inject(Router);
  return (await auth.currentUser()) ? true : router.createUrlTree(['/login']);
};

export const anonymousGuard: CanActivateFn = async () => {
  const auth = inject(FirebaseAuthService);
  const router = inject(Router);
  return (await auth.currentUser()) ? router.createUrlTree(['/']) : true;
};
