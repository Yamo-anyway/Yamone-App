-- Applied to Yamone as developer_dataset_rpc_anon_grants.
-- Supabase project-level default privileges may independently grant anon EXECUTE.
revoke all on function public.dev_dataset_allowed(), public.dev_dataset_enroll(text), public.dev_dataset_begin(uuid,jsonb), public.dev_dataset_commit_part(uuid,jsonb), public.dev_dataset_finalize(uuid,jsonb), public.dev_dataset_storage_insert(text), public.dev_dataset_admin_invite(), public.dev_dataset_admin_list(), public.dev_dataset_admin_export(uuid) from anon;
comment on table public.dev_dataset_invites is 'Server-only one-use pairing tokens. RLS intentionally has no client policies.';
comment on table public.dev_dataset_testers is 'Server-only approved tester IDs. RLS intentionally has no client policies.';
