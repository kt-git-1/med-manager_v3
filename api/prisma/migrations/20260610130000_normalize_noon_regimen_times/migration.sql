WITH target_regimens AS (
  SELECT
    r."id",
    t.value,
    t.ordinality
  FROM "Regimen" r
  CROSS JOIN LATERAL unnest(r."times") WITH ORDINALITY AS t(value, ordinality)
  WHERE '12:00' = ANY(r."times")
),
normalized AS (
  SELECT
    "id",
    CASE
      WHEN value = '12:00' THEN 'noon'
      ELSE value
    END AS value,
    ordinality
  FROM target_regimens
),
deduped AS (
  SELECT
    "id",
    value,
    MIN(ordinality) AS ordinality
  FROM normalized
  GROUP BY "id", value
),
rebuilt AS (
  SELECT
    "id",
    array_agg(value ORDER BY ordinality) AS times
  FROM deduped
  GROUP BY "id"
)
UPDATE "Regimen" r
SET
  "times" = rebuilt.times,
  "updatedAt" = NOW()
FROM rebuilt
WHERE r."id" = rebuilt."id"
  AND r."times" IS DISTINCT FROM rebuilt.times;
