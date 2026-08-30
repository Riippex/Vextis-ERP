import { Pipe, PipeTransform, SecurityContext, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Marked, Renderer } from 'marked';

function escapeHtml(value: string): string {
  return value.replace(
    /[&<>"']/g,
    (character) =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[character] ?? character,
  );
}

const renderer = new Renderer();

// Model output and conversation history are untrusted content. Raw HTML is
// displayed literally and Markdown images are reduced to alt text so a reply
// cannot inject UI or trigger an external tracking request.
renderer.html = ({ text }) => escapeHtml(text);
renderer.image = ({ text }) => `<span class="vxt-markdown__image-alt">Image: ${escapeHtml(text)}</span>`;

const markdown = new Marked({
  async: false,
  breaks: true,
  gfm: true,
  renderer,
});

@Pipe({
  name: 'vxtSafeMarkdown',
  standalone: true,
  pure: true,
})
export class SafeMarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(content: string): string {
    const rendered = markdown.parse(content, { async: false });
    return this.sanitizer.sanitize(SecurityContext.HTML, rendered) ?? '';
  }
}
