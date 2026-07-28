-- Add check-out timestamp to attendance table
ALTER TABLE attendance
  ADD COLUMN IF NOT EXISTS "checkedOutAt" TIMESTAMP;
