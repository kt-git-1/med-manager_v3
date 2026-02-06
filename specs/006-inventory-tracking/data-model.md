# Data Model: Inventory Tracking

## Entities

### Medication (Inventory Fields)

- **inventoryEnabled**: boolean (default false)
- **inventoryQuantity**: integer count (default 0)
- **inventoryLowThreshold**: integer threshold (default 0)
- **inventoryUpdatedAt**: timestamp
- **inventoryLastAlertState**: enum (NONE | LOW | OUT) or null

### MedicationInventoryAdjustment

- **id**: unique identifier
- **patientId**: linked patient
- **medicationId**: linked medication
- **delta**: integer change applied (+/-)
- **reason**: REFILL | SET | CORRECTION | TAKEN_CREATE | TAKEN_DELETE
- **actorType**: caregiver | system
- **actorId**: optional caregiver identifier
- **createdAt**: timestamp

### InventoryAlertEvent

- **id**: unique identifier
- **patientId**: linked patient
- **medicationId**: linked medication
- **type**: LOW | OUT
- **remaining**: integer at time of event
- **threshold**: integer threshold at time of event
- **patientDisplayName**: optional patient display name for caregiver banners
- **medicationName**: optional medication name for caregiver banners
- **createdAt**: timestamp

## Relationships

- **Medication** belongs to **Patient** (existing relationship).
- **MedicationInventoryAdjustment** belongs to **Medication** and **Patient**.
- **InventoryAlertEvent** belongs to **Medication** and **Patient**; readable only by linked caregivers.

## Validation & Rules

- Inventory quantities are whole-number counts and must not drop below zero.
- Low state is defined as remaining < threshold; out-of-stock is remaining == 0.
- Inventory adjustments occur only when inventoryEnabled is true.
- Alert events are emitted only on transitions into LOW or OUT.
