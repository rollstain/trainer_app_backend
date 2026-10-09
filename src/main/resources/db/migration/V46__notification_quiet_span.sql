alter table notifications add column quiet_from timestamptz;
alter table notifications add column quiet_until timestamptz;
alter table notifications add column pushed_at timestamptz;
