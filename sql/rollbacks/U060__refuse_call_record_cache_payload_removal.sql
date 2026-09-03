-- V060 is forward-only because cache payload history is part of the call-record contract.
DO $$
BEGIN
    RAISE EXCEPTION
        'U060 is intentionally refused: do not remove call_record.cache_response_data in place';
END
$$;
