// SPEC §4 reference strings the API does not enumerate: the four fraud checks (the detail returns
// only flagged ones, but the checks card shows FLAG/CLEAR for all four) and the five override codes
// (the review form lists them). Static, deterministic template text — not application data.
export const FRAUD_CHECKS: [string, string][] = [
  ['F01', 'Address does not match the credit file'],
  ['F02', 'SSN issuance precedes date of birth'],
  ['F03', 'SSN reported deceased'],
  ['F04', 'Application velocity limit exceeded'],
]

export const OVERRIDE_CODES: [string, string][] = [
  ['O1', 'Identity verified with documents'],
  ['O2', 'Income verified'],
  ['O3', 'Fraud confirmed'],
  ['O4', 'Bureau data corrected'],
  ['O5', 'Credit policy exception'],
]
