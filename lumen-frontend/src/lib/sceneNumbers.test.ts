import { describe, expect, it } from 'vitest';
import { numberAt, numberBetween } from './sceneNumbers';

describe('numberBetween', () => {
  it('prefers the next whole number', () => {
    expect(numberBetween('2', '5')).toBe('3');
    expect(numberBetween('2.5', '5')).toBe('3');
    expect(numberBetween('7', null)).toBe('8');
    expect(numberBetween('7.25', null)).toBe('8');
    expect(numberBetween(null, null)).toBe('1');
  });

  it('uses as few decimals as possible in tight spots', () => {
    expect(numberBetween('2', '3')).toBe('2.5');
    expect(numberBetween('2.5', '3')).toBe('2.8');
    expect(numberBetween('2.9', '3')).toBe('2.95');
    expect(numberBetween(null, '1')).toBe('0.5');
    expect(numberBetween(null, '3')).toBe('1');
  });

  it('gives up when nothing fits', () => {
    expect(numberBetween('2.001', '2.002')).toBeNull();
    expect(numberBetween('9999', null)).toBeNull();
  });
});

describe('numberAt', () => {
  it('numbers a scene dragged between two others', () => {
    const others = ['1', '2', '3'];
    expect(numberAt(others, 0)).toBe('0.5');
    expect(numberAt(others, 1)).toBe('1.5');
    expect(numberAt(others, 3)).toBe('4');
  });
});
