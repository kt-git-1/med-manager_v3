import { getDayRange, getLocalDateKey, getScheduleWithStatus } from "./scheduleService";
import { getPatientSlotTimeTimeline } from "./patientSlotTimeService";

export type HistoryStreakTodayStatus = "complete" | "inProgress" | "missed" | "noSchedule";

export type HistoryStreakDose = {
  scheduledAt: string;
  effectiveStatus?: "pending" | "taken" | "missed";
};

export type HistoryStreakResult = {
  currentStreakDays: number;
  isAtLeast: boolean;
  todayStatus: HistoryStreakTodayStatus;
};

const historyTimeZone = "Asia/Tokyo";
const maxDisplayedStreakDays = 365;
const streakLookbackDays = 400;

function statusForDoses(doses: HistoryStreakDose[]): HistoryStreakTodayStatus {
  if (doses.length === 0) return "noSchedule";
  if (doses.every((dose) => dose.effectiveStatus === "taken")) return "complete";
  if (doses.some((dose) => dose.effectiveStatus === "missed")) return "missed";
  return "inProgress";
}

export function calculateHistoryStreak(
  doses: HistoryStreakDose[],
  now: Date,
  timeZone = historyTimeZone,
  maximumDays = maxDisplayedStreakDays
): HistoryStreakResult {
  const todayKey = getLocalDateKey(now, timeZone);
  const dosesByDate = new Map<string, HistoryStreakDose[]>();
  for (const dose of doses) {
    const dateKey = getLocalDateKey(new Date(dose.scheduledAt), timeZone);
    const existing = dosesByDate.get(dateKey) ?? [];
    existing.push(dose);
    dosesByDate.set(dateKey, existing);
  }

  const todayStatus = statusForDoses(dosesByDate.get(todayKey) ?? []);
  if (todayStatus === "missed") {
    return { currentStreakDays: 0, isAtLeast: false, todayStatus };
  }

  const scheduledDateKeys = [...dosesByDate.keys()]
    .filter((dateKey) => dateKey <= todayKey)
    .sort((left, right) => right.localeCompare(left));

  let currentStreakDays = 0;
  for (const dateKey of scheduledDateKeys) {
    const status = statusForDoses(dosesByDate.get(dateKey) ?? []);
    if (dateKey === todayKey && status === "inProgress") {
      continue;
    }
    if (status !== "complete") {
      break;
    }
    currentStreakDays += 1;
    if (currentStreakDays >= maximumDays) {
      return { currentStreakDays: maximumDays, isAtLeast: true, todayStatus };
    }
  }

  return { currentStreakDays, isAtLeast: false, todayStatus };
}

export async function getPatientHistoryStreak(
  patientId: string,
  now = new Date()
): Promise<HistoryStreakResult> {
  const todayRange = getDayRange(now, historyTimeZone);
  const from = new Date(todayRange.from.getTime() - streakLookbackDays * 24 * 60 * 60 * 1000);
  const slotTimeTimeline = await getPatientSlotTimeTimeline(patientId, from, todayRange.to);
  const doses = await getScheduleWithStatus(
    patientId,
    from,
    todayRange.to,
    historyTimeZone,
    now,
    undefined,
    slotTimeTimeline
  );
  return calculateHistoryStreak(doses, now, historyTimeZone);
}
