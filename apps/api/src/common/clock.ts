/**
 * Time is a dependency, not a global. Code that calls `new Date()` directly can't be tested
 * deterministically ("was this within 15 minutes?"). Tests pass a fixed clock instead.
 */
export interface Clock {
  now(): Date;
}

export const systemClock: Clock = { now: () => new Date() };

/** Bank SMS timestamps are Indian wall-clock time. */
export const IST_OFFSET = "+05:30";
