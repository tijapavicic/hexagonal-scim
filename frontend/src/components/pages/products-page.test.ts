import { describe, it, expect } from 'vitest';
import './products-page';

describe('products-page', () => {
  it('should be defined as a custom element', () => {
    const element = document.createElement('products-page');
    expect(element).toBeInstanceOf(HTMLElement);
  });
});

