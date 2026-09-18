alter table slot_change_requests
    add column original_starts_at timestamptz,
    add column coach_comment text;

update slot_change_requests r
set original_starts_at = s.starts_at
from training_slots s
where r.slot_id = s.id
  and r.status = 'PENDING';

create index slot_change_requests_requester_idx
    on slot_change_requests (requested_by_user_id, slot_id);
