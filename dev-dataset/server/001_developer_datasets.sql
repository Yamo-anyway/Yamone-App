-- Private, tester-enrolled diagnostic datasets. No change to existing activity/sleep tables.
create table public.dev_dataset_testers (
  user_id uuid primary key references auth.users(id) on delete cascade,
  active boolean not null default true,
  enrolled_at timestamptz not null default now()
);
create table public.dev_dataset_invites (
  token_hash text primary key,
  expires_at timestamptz not null,
  used_by uuid references auth.users(id),
  used_at timestamptz
);
create table public.dev_dataset_sessions (
  id uuid primary key,
  owner_id uuid not null references auth.users(id),
  status text not null default 'uploading' check(status in ('uploading','complete')),
  manifest jsonb not null,
  final_manifest jsonb,
  part_count integer,
  event_count bigint,
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
create index dev_dataset_sessions_owner on public.dev_dataset_sessions(owner_id);
create table public.dev_dataset_parts (
  session_id uuid not null references public.dev_dataset_sessions(id) on delete cascade,
  part integer not null check(part between 0 and 999999),
  first_seq bigint not null check(first_seq>=0),
  last_seq bigint not null check(last_seq>=first_seq),
  event_count bigint not null check(event_count>0),
  bytes bigint not null check(bytes>0 and bytes<=8388608),
  sha256 text not null check(sha256 ~ '^[a-f0-9]{64}$'),
  object_path text not null unique,
  detail jsonb not null,
  created_at timestamptz not null default now(),
  primary key(session_id,part),
  check(event_count=last_seq-first_seq+1)
);
alter table public.dev_dataset_testers enable row level security;
alter table public.dev_dataset_invites enable row level security;
alter table public.dev_dataset_sessions enable row level security;
alter table public.dev_dataset_parts enable row level security;
revoke all on public.dev_dataset_invites,public.dev_dataset_testers,public.dev_dataset_sessions,public.dev_dataset_parts from anon,authenticated;
grant select on public.dev_dataset_sessions,public.dev_dataset_parts to authenticated;

create function public.dev_dataset_allowed() returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and exists(select 1 from public.dev_dataset_testers where user_id=auth.uid() and active);
$$;
revoke all on function public.dev_dataset_allowed() from public;
grant execute on function public.dev_dataset_allowed() to authenticated;
create policy dev_dataset_sessions_read on public.dev_dataset_sessions for select to authenticated
 using(public.dev_dataset_allowed() and owner_id=(select auth.uid()));
create policy dev_dataset_parts_read on public.dev_dataset_parts for select to authenticated
 using(public.dev_dataset_allowed() and exists(select 1 from public.dev_dataset_sessions s where s.id=session_id and s.owner_id=(select auth.uid())));

create function public.dev_dataset_enroll(p_code text) returns jsonb language plpgsql security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception 'authentication_required'; end if;
  if public.dev_dataset_allowed() then return jsonb_build_object('ok',true); end if;
  if p_code is null or p_code !~ '^[a-f0-9]{32}$' then raise exception 'invalid_pairing_code'; end if;
  update public.dev_dataset_invites set used_by=auth.uid(),used_at=now()
    where token_hash=encode(extensions.digest(p_code,'sha256'),'hex') and used_at is null and expires_at>now();
  if not found then raise exception 'invalid_or_expired_pairing_code'; end if;
  insert into public.dev_dataset_testers(user_id) values(auth.uid()) on conflict(user_id) do update set active=true;
  return jsonb_build_object('ok',true);
end;
$$;
create function public.dev_dataset_begin(p_id uuid,p_manifest jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.dev_dataset_sessions;
begin
  if not public.dev_dataset_allowed() then raise exception 'tester_not_enrolled'; end if;
  if p_id is null or p_manifest is null or octet_length(p_manifest::text)>65536 or (p_manifest->>'schemaVersion')::int is distinct from 1 or (p_manifest->>'id')::uuid is distinct from p_id then raise exception 'invalid_manifest'; end if;
  insert into public.dev_dataset_sessions(id,owner_id,manifest) values(p_id,auth.uid(),p_manifest) on conflict(id) do nothing;
  select * into s from public.dev_dataset_sessions where id=p_id;
  if s.owner_id<>auth.uid() then raise exception 'forbidden'; end if;
  return jsonb_build_object('ok',true,'status',s.status);
end;
$$;
create function public.dev_dataset_commit_part(p_id uuid,p_part jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.dev_dataset_sessions; old public.dev_dataset_parts; n integer; h text; path text; object_bytes bigint;
begin
  if not public.dev_dataset_allowed() then raise exception 'tester_not_enrolled'; end if;
  select * into s from public.dev_dataset_sessions where id=p_id and owner_id=auth.uid() for update;
  if not found then raise exception 'session_not_found'; end if;
  if p_part is null or (p_part->>'part') is null or (p_part->>'sha256') is null or (p_part->>'bytes') is null or (p_part->>'firstSeq') is null or (p_part->>'lastSeq') is null or (p_part->>'eventCount') is null then raise exception 'invalid_part'; end if;
  n=(p_part->>'part')::integer; h=p_part->>'sha256';
  path=auth.uid()::text||'/'||p_id::text||'/'||lpad(n::text,6,'0')||'-'||h||'.jsonl.gz';
  select * into old from public.dev_dataset_parts where session_id=p_id and part=n;
  if found then
    if old.sha256<>h or old.bytes<>(p_part->>'bytes')::bigint or old.first_seq<>(p_part->>'firstSeq')::bigint or old.last_seq<>(p_part->>'lastSeq')::bigint or old.event_count<>(p_part->>'eventCount')::bigint then raise exception 'part_conflict'; end if;
    return jsonb_build_object('ok',true,'duplicate',true);
  end if;
  if s.status='complete' then raise exception 'session_already_complete'; end if;
  if octet_length(p_part::text)>16384 then raise exception 'part_metadata_too_large'; end if;
  select (metadata->>'size')::bigint into object_bytes from storage.objects where bucket_id='developer-datasets' and name=path;
  if object_bytes is null or object_bytes<>(p_part->>'bytes')::bigint then raise exception 'object_missing_or_wrong_size'; end if;
  insert into public.dev_dataset_parts(session_id,part,first_seq,last_seq,event_count,bytes,sha256,object_path,detail)
    values(p_id,n,(p_part->>'firstSeq')::bigint,(p_part->>'lastSeq')::bigint,(p_part->>'eventCount')::bigint,object_bytes,h,path,p_part);
  return jsonb_build_object('ok',true,'duplicate',false);
end;
$$;
create function public.dev_dataset_finalize(p_id uuid,p_manifest jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.dev_dataset_sessions; expected integer; events bigint; bad boolean;
begin
  if not public.dev_dataset_allowed() then raise exception 'tester_not_enrolled'; end if;
  select * into s from public.dev_dataset_sessions where id=p_id and owner_id=auth.uid() for update;
  if not found then raise exception 'session_not_found'; end if;
  if p_manifest is null or octet_length(p_manifest::text)>65536 or p_manifest->>'state' is distinct from 'closed' or (p_manifest->>'id')::uuid is distinct from p_id or (p_manifest->>'schemaVersion')::int is distinct from 1 then raise exception 'invalid_final_manifest'; end if;
  expected=(p_manifest->>'partCount')::integer;events=(p_manifest->>'eventCount')::bigint;
  if expected is null or expected<1 or events is null or events<1 then raise exception 'invalid_counts'; end if;
  if s.status='complete' then
    if s.part_count<>expected or s.event_count<>events then raise exception 'final_conflict'; end if;
    return jsonb_build_object('ok',true,'complete',true);
  end if;
  if (select count(*) from public.dev_dataset_parts where session_id=p_id)<>expected then raise exception 'missing_parts'; end if;
  select exists(select 1 from (
    select part,first_seq,last_seq,lag(last_seq,1,-1::bigint) over(order by part) prev,row_number() over(order by part)-1 idx
    from public.dev_dataset_parts where session_id=p_id
  ) p where p.part<>p.idx or p.first_seq<>p.prev+1) into bad;
  if bad or (select max(last_seq)+1 from public.dev_dataset_parts where session_id=p_id)<>events then raise exception 'event_sequence_gap'; end if;
  update public.dev_dataset_sessions set status='complete',final_manifest=p_manifest,part_count=expected,event_count=events,completed_at=now() where id=p_id;
  return jsonb_build_object('ok',true,'complete',true);
end;
$$;
revoke all on function public.dev_dataset_enroll(text),public.dev_dataset_begin(uuid,jsonb),public.dev_dataset_commit_part(uuid,jsonb),public.dev_dataset_finalize(uuid,jsonb) from public;
grant execute on function public.dev_dataset_enroll(text),public.dev_dataset_begin(uuid,jsonb),public.dev_dataset_commit_part(uuid,jsonb),public.dev_dataset_finalize(uuid,jsonb) to authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('developer-datasets','developer-datasets',false,8388608,array['application/gzip']);
create function public.dev_dataset_storage_insert(p_name text) returns boolean language plpgsql security definer set search_path='' as $$
declare sid uuid; used bigint;
begin
  if not public.dev_dataset_allowed() or split_part(p_name,'/',1)<>auth.uid()::text then return false; end if;
  if p_name !~ '^[a-f0-9-]{36}/[a-f0-9-]{36}/[0-9]{6}-[a-f0-9]{64}\.jsonl\.gz$' then return false; end if;
  sid=split_part(p_name,'/',2)::uuid;
  if not exists(select 1 from public.dev_dataset_sessions where id=sid and owner_id=auth.uid() and status='uploading') then return false; end if;
  perform pg_advisory_xact_lock(36100001);
  select coalesce(sum((metadata->>'size')::bigint),0) into used from storage.objects where bucket_id='developer-datasets';
  -- Conservative test-storage guard; does not change the project's billing plan.
  return used+8388608<=268435456;
end;
$$;
revoke all on function public.dev_dataset_storage_insert(text) from public;
grant execute on function public.dev_dataset_storage_insert(text) to authenticated;
create policy dev_dataset_object_insert on storage.objects for insert to authenticated
 with check(bucket_id='developer-datasets' and public.dev_dataset_storage_insert(name));
create policy dev_dataset_object_read on storage.objects for select to authenticated
 using(bucket_id='developer-datasets' and public.dev_dataset_allowed() and (storage.foldername(name))[1]=(select auth.uid())::text);
-- No client UPDATE/DELETE policy: acknowledged parts cannot silently change.

-- Administrative access is checked on the server, not by the Web UI.
create function public.dev_dataset_admin_invite() returns jsonb language plpgsql security definer set search_path='' as $$
declare code text;
begin
  if not coalesce(public.is_admin(),false) then raise exception 'admin_required'; end if;
  code=encode(extensions.gen_random_bytes(16),'hex');
  insert into public.dev_dataset_invites(token_hash,expires_at) values(encode(extensions.digest(code,'sha256'),'hex'),now()+interval '24 hours');
  return jsonb_build_object('code',code,'expiresAt',now()+interval '24 hours');
end;
$$;
create function public.dev_dataset_admin_list() returns jsonb language plpgsql security definer set search_path='' as $$
begin
  if not coalesce(public.is_admin(),false) then raise exception 'admin_required'; end if;
  return coalesce((select jsonb_agg(x order by x.created_at desc) from (
    select s.id,s.status,s.manifest->>'mode' as mode,s.manifest->>'startWallMs' as start_ms,
      s.created_at,s.completed_at,s.part_count,s.event_count,
      (select count(*) from public.dev_dataset_parts p where p.session_id=s.id) as received_parts,
      (select coalesce(sum(bytes),0) from public.dev_dataset_parts p where p.session_id=s.id) as stored_bytes
    from public.dev_dataset_sessions s order by s.created_at desc limit 200
  ) x),'[]'::jsonb);
end;
$$;
create function public.dev_dataset_admin_export(p_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare s public.dev_dataset_sessions;
begin
  if not coalesce(public.is_admin(),false) then raise exception 'admin_required'; end if;
  select * into s from public.dev_dataset_sessions where id=p_id;
  if not found then raise exception 'session_not_found'; end if;
  if s.status<>'complete' then raise exception 'session_not_complete'; end if;
  return jsonb_build_object('manifest',s.final_manifest,'parts',
    coalesce((select jsonb_agg(jsonb_build_object('detail',p.detail,'path',p.object_path) order by p.part) from public.dev_dataset_parts p where p.session_id=p_id),'[]'::jsonb));
end;
$$;
revoke all on function public.dev_dataset_admin_invite(),public.dev_dataset_admin_list(),public.dev_dataset_admin_export(uuid) from public;
grant execute on function public.dev_dataset_admin_invite(),public.dev_dataset_admin_list(),public.dev_dataset_admin_export(uuid) to authenticated;
create policy dev_dataset_object_admin_read on storage.objects for select to authenticated
 using(bucket_id='developer-datasets' and (select public.is_admin()));
