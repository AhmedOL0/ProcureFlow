-- Scale seed: 10,000 approved/submitted requests + 20,000 lines for
-- tenant loadscale-001. Read-load measurement only: rows bypass the
-- application (no audit rows, no events), which is fine because the
-- measured paths are reads. \set TID/UID first (see README).
WITH g AS (SELECT generate_series(1, 10000) AS n)
INSERT INTO purchase_requests
    (id, tenant_id, requester_id, title, status, idempotency_key,
     submitted_at, decided_at, created_at)
SELECT gen_random_uuid(), :'TID', :'UID',
    'Load request ' || n,
    CASE WHEN n % 5 = 0 THEN 'SUBMITTED' ELSE 'APPROVED' END,
    'loadscale-' || n,
    now() - ((n % 365) || ' days')::interval,
    CASE WHEN n % 5 = 0 THEN NULL
         ELSE now() - ((n % 365) || ' days')::interval + interval '2 hours' END,
    now() - ((n % 365) || ' days')::interval
FROM g;

INSERT INTO purchase_request_items
    (id, request_id, description, category, quantity, unit_price_minor, currency)
SELECT gen_random_uuid(), r.id, 'Load line ' || l.n, 'Hardware', 2, 89900, 'MAD'
FROM purchase_requests r
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) l
WHERE r.tenant_id = :'TID' AND r.title LIKE 'Load request %';

SELECT (SELECT count(*) FROM purchase_requests WHERE tenant_id = :'TID') AS requests,
       (SELECT count(*) FROM purchase_request_items WHERE request_id IN
            (SELECT id FROM purchase_requests WHERE tenant_id = :'TID')) AS items;
