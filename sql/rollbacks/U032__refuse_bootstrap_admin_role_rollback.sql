DO $$
BEGIN
    RAISE EXCEPTION
        'bootstrap admin role assignment is forward-only; revoke it explicitly through IAM if required';
END $$;
