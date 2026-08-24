import { Injectable, signal } from '@angular/core';
import { FirebaseApp, FirebaseOptions, initializeApp } from 'firebase/app';
import {
  Auth,
  User,
  browserLocalPersistence,
  getAuth,
  onAuthStateChanged,
  setPersistence,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';

const FIREBASE_RUNTIME_CONFIG_URL = '/__/firebase/init.json';

@Injectable({ providedIn: 'root' })
export class FirebaseAuthService {
  readonly user = signal<User | null>(null);

  private initialization: Promise<Auth> | null = null;

  async signIn(email: string, password: string): Promise<void> {
    const auth = await this.initialize();
    await signInWithEmailAndPassword(auth, email, password);
  }

  async signOut(): Promise<void> {
    const auth = await this.initialize();
    await signOut(auth);
  }

  async idToken(): Promise<string | null> {
    const auth = await this.initialize();
    return auth.currentUser?.getIdToken() ?? null;
  }

  async currentUser(): Promise<User | null> {
    const auth = await this.initialize();
    await auth.authStateReady();
    return auth.currentUser;
  }

  private initialize(): Promise<Auth> {
    this.initialization ??= this.createAuth();
    return this.initialization;
  }

  private async createAuth(): Promise<Auth> {
    const response = await fetch(FIREBASE_RUNTIME_CONFIG_URL, {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new Error(`Firebase runtime configuration is unavailable (${response.status}).`);
    }

    const config = (await response.json()) as FirebaseOptions;
    const app: FirebaseApp = initializeApp(config);
    const auth = getAuth(app);
    await setPersistence(auth, browserLocalPersistence);
    onAuthStateChanged(auth, (user) => this.user.set(user));
    return auth;
  }
}
