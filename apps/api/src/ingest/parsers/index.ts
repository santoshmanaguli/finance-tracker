import type { SmsParser } from "./types.ts";

/**
 * Production bank parsers. Empty until the HDFC / SBI / OneCard parsers are written from real
 * (masked) sample SMS (roadmap Step 5). Until then every SMS lands in the unparsed inbox,
 * which is the designed behaviour: nothing is dropped, and it can be re-parsed later.
 */
export const bankParsers: readonly SmsParser[] = [];
