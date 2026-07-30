ALTER TABLE billing_plan
  DROP CONSTRAINT IF EXISTS ex_billing_plan_effective_window;

-- btree_gist may be shared by other schemas or later migrations, so rollback intentionally keeps it installed.
