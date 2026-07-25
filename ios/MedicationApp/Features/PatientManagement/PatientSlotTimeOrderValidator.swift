import Foundation

enum PatientSlotTimeOrderValidator {
    static func validationMessage(for slotTimes: PatientSlotTimesDTO) -> String? {
        guard
            let morning = minutes(from: slotTimes.morning),
            let noon = minutes(from: slotTimes.noon),
            let evening = minutes(from: slotTimes.evening),
            let bedtime = minutes(from: slotTimes.bedtime)
        else {
            return NSLocalizedString(
                "caregiver.timePreset.validation.invalid",
                comment: "Invalid medication time preset"
            )
        }

        if noon <= morning {
            return NSLocalizedString(
                "caregiver.timePreset.validation.noon",
                comment: "Noon must be after morning"
            )
        }
        if evening <= noon {
            return NSLocalizedString(
                "caregiver.timePreset.validation.evening",
                comment: "Evening must be after noon"
            )
        }
        if bedtime <= evening {
            return NSLocalizedString(
                "caregiver.timePreset.validation.bedtime",
                comment: "Bedtime must be after evening"
            )
        }
        return nil
    }

    private static func minutes(from value: String) -> Int? {
        let parts = value.split(separator: ":")
        guard
            parts.count == 2,
            let hour = Int(parts[0]),
            let minute = Int(parts[1]),
            (0...23).contains(hour),
            (0...59).contains(minute)
        else {
            return nil
        }
        return hour * 60 + minute
    }
}
