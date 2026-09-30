alter table coach_clients
    add column ended_at timestamptz,
    add column ended_by text check (ended_by in ('CLIENT', 'COACH'));
