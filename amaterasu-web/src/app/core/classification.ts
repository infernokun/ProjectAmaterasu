/** Banner markings an admin can choose; the API accepts the same values. NONE hides the banner. */
export type Classification = 'NONE' | 'UNCLASSIFIED' | 'CUI' | 'CONFIDENTIAL' | 'SECRET' | 'TOP_SECRET' | 'TOP_SECRET_SCI';

export interface ClassificationOption {
  value: Classification;
  /** What the banner says. */
  label: string;
  background: string;
  color: string;
}

/** The standard banner colors for each marking. */
export const CLASSIFICATIONS: ClassificationOption[] = [
  { value: 'UNCLASSIFIED', label: 'UNCLASSIFIED', background: '#007a33', color: '#ffffff' },
  { value: 'CUI', label: 'CUI', background: '#502b85', color: '#ffffff' },
  { value: 'CONFIDENTIAL', label: 'CONFIDENTIAL', background: '#0033a0', color: '#ffffff' },
  { value: 'SECRET', label: 'SECRET', background: '#c8102e', color: '#ffffff' },
  { value: 'TOP_SECRET', label: 'TOP SECRET', background: '#ff8c00', color: '#000000' },
  { value: 'TOP_SECRET_SCI', label: 'TOP SECRET//SCI', background: '#fce83a', color: '#000000' },
  { value: 'NONE', label: 'No banner', background: 'transparent', color: 'inherit' },
];

export const DEFAULT_CLASSIFICATION: Classification = 'UNCLASSIFIED';

/** The option for a stored value; anything unknown falls back to UNCLASSIFIED. */
export function classificationOf(value: unknown): ClassificationOption {
  return CLASSIFICATIONS.find(option => option.value === value)
    ?? CLASSIFICATIONS.find(option => option.value === DEFAULT_CLASSIFICATION)!;
}
