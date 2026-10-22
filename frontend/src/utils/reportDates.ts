export function shiftDate(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

export function previousMonday(date: string): string {
  const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
  const daysSinceMonday = (weekday + 6) % 7;
  return shiftDate(date, -(daysSinceMonday + 7));
}
