import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AskVextisConversationGQL, AskVextisGQL } from '../../api/generated/graphql';
import { AskVextisChatStore } from './ask-vextis-chat.store';
import { AskVextisPanelComponent } from './ask-vextis-panel.component';

describe('AskVextisPanelComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AskVextisPanelComponent],
      providers: [
        {
          provide: AskVextisGQL,
          useValue: {
            mutate: vi.fn().mockReturnValue(
              of({
                data: {
                  askVextis: {
                    conversationId: 'conv-1',
                    messageId: 'msg-1',
                    reply: 'Placeholder reply',
                    createdAt: '2026-08-25T12:00:00Z',
                  },
                },
              }),
            ),
          },
        },
        { provide: AskVextisConversationGQL, useValue: { fetch: vi.fn() } },
      ],
    });
  });

  it('is hidden until the store opens it, then renders sent messages', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.ask-vextis-panel') as HTMLElement;
    expect(panel.classList.contains('ask-vextis-panel--open')).toBe(false);
    expect(fixture.nativeElement.querySelector('.ask-vextis-backdrop')).toBeNull();

    store.openPanel();
    fixture.detectChanges();
    expect(panel.classList.contains('ask-vextis-panel--open')).toBe(true);
    expect(fixture.nativeElement.querySelector('.ask-vextis-backdrop')).toBeTruthy();

    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Hello Vextis';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Hello Vextis');
    expect(store.messages()).toHaveLength(2);
  });

  it('closes on backdrop click', () => {
    const fixture = TestBed.createComponent(AskVextisPanelComponent);
    const store = TestBed.inject(AskVextisChatStore);
    store.openPanel();
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.ask-vextis-backdrop') as HTMLElement).click();
    fixture.detectChanges();

    expect(store.open()).toBe(false);
  });
});
